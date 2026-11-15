/**
 * Resilient Server-Sent Events client over `fetch` + `ReadableStream` (`EventSource` cannot send an
 * `Authorization` header): reconnects with exponential backoff and resumes with `Last-Event-ID`.
 */
import { createSseParser, type SseMessage } from "./parser";

export interface SseConnectOptions {
  url: string;
  /** Resolves request headers (e.g. a freshly refreshed bearer token) for every (re)connect. */
  getHeaders?: () => Promise<Record<string, string>>;
  /** Initial `Last-Event-ID` value (the backend replays everything after it). */
  lastEventId?: string | null;
  onMessage: (message: SseMessage) => void;
  onOpen?: () => void;
  /** Called on each failed attempt, with the delay before the next one. */
  onError?: (error: unknown, retryInMs: number) => void;
  /** Stop permanently on these HTTP statuses (default 401, 403, 404). */
  fatalStatuses?: number[];
  onFatal?: (status: number) => void;
  initialBackoffMs?: number;
  maxBackoffMs?: number;
  fetchImpl?: typeof fetch;
}

export interface SseConnection {
  close: () => void;
  readonly lastEventId: string | null;
}

export class SseHttpError extends Error {
  constructor(readonly status: number) {
    super(`SSE request failed with HTTP ${status}`);
  }
}

export function backoffDelay(attempt: number, initialMs: number, maxMs: number): number {
  return Math.min(maxMs, initialMs * 2 ** Math.max(0, attempt));
}

/**
 * Opens a resilient event stream: reconnects with exponential backoff and resumes with
 * `Last-Event-ID` set to the last id seen. Call `close()` to stop (e.g. after an `end` event).
 */
export function connectSse(options: SseConnectOptions): SseConnection {
  const fetchImpl = options.fetchImpl ?? fetch.bind(globalThis);
  const initial = options.initialBackoffMs ?? 500;
  const max = options.maxBackoffMs ?? 15_000;
  const fatal = options.fatalStatuses ?? [401, 403, 404];
  let lastEventId = options.lastEventId ?? null;
  let closed = false;
  let attempt = 0;
  let controller: AbortController | null = null;
  let timer: ReturnType<typeof setTimeout> | null = null;

  const parser = createSseParser((message) => {
    if (message.id !== null) lastEventId = message.id;
    if (!closed) options.onMessage(message);
  });

  const scheduleReconnect = (error: unknown) => {
    if (closed) return;
    const delay = backoffDelay(attempt, initial, max);
    attempt++;
    options.onError?.(error, delay);
    timer = setTimeout(run, delay);
  };

  async function run(): Promise<void> {
    if (closed) return;
    controller = new AbortController();
    parser.reset();
    try {
      const headers: Record<string, string> = {
        // Only CORS-safelisted or explicitly allowed headers (Authorization, Last-Event-ID).
        Accept: "text/event-stream",
        ...(options.getHeaders ? await options.getHeaders() : {}),
      };
      if (lastEventId !== null) headers["Last-Event-ID"] = lastEventId;
      const response = await fetchImpl(options.url, { headers, signal: controller.signal, cache: "no-store" });
      if (!response.ok || !response.body) {
        if (fatal.includes(response.status)) {
          closed = true;
          options.onFatal?.(response.status);
          return;
        }
        throw new SseHttpError(response.status);
      }
      attempt = 0;
      options.onOpen?.();
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      while (!closed) {
        const { value, done } = await reader.read();
        if (done) break;
        parser.push(decoder.decode(value, { stream: true }));
      }
      reader.releaseLock();
      if (!closed) scheduleReconnect(new Error("Stream ended"));
    } catch (error) {
      if (closed) return;
      scheduleReconnect(error);
    }
  }

  void run();

  return {
    close() {
      closed = true;
      if (timer) clearTimeout(timer);
      controller?.abort();
    },
    get lastEventId() {
      return lastEventId;
    },
  };
}
