import { afterEach, describe, expect, it, vi } from "vitest";
import { backoffDelay, connectSse } from "./connect";
import { createSseParser, type SseMessage } from "./parser";

function collect() {
  const messages: SseMessage[] = [];
  const parser = createSseParser((m) => messages.push(m));
  return { messages, parser };
}

describe("createSseParser", () => {
  it("parses id, event and data fields (with and without a space after the colon)", () => {
    const { messages, parser } = collect();
    parser.push('id:11\nevent:transition\ndata:{"sequence":11}\n\nid: 12\nevent: end\ndata: {}\n\n');
    expect(messages).toEqual([
      { id: "11", event: "transition", data: '{"sequence":11}', retry: undefined },
      { id: "12", event: "end", data: "{}", retry: undefined },
    ]);
  });

  it("handles chunk boundaries anywhere, including inside CRLF", () => {
    const { messages, parser } = collect();
    const stream = 'id: 7\r\nevent: transition\r\ndata: {"a":\r\ndata: 1}\r\n\r\n';
    for (const ch of stream) parser.push(ch);
    expect(messages).toHaveLength(1);
    expect(messages[0]).toMatchObject({ id: "7", event: "transition", data: '{"a":\n1}' });
  });

  it("joins multi-line data with newlines", () => {
    const { messages, parser } = collect();
    parser.push("data: line 1\ndata: line 2\ndata:\n\n");
    expect(messages[0].data).toBe("line 1\nline 2\n");
    expect(messages[0].event).toBe("message");
  });

  it("ignores comments / keep-alives and unknown fields", () => {
    const { messages, parser } = collect();
    parser.push(": keep-alive\n\nfoo: bar\n\n:another\ndata: x\n\n");
    expect(messages).toHaveLength(1);
    expect(messages[0].data).toBe("x");
  });

  it("keeps the last event id across events and parses retry", () => {
    const { messages, parser } = collect();
    parser.push("id: 5\nretry: 3000\ndata: a\n\ndata: b\n\n");
    expect(messages[0]).toMatchObject({ id: "5", retry: 3000 });
    expect(messages[1]).toMatchObject({ id: "5", data: "b" });
  });

  it("does not dispatch an incomplete event", () => {
    const { messages, parser } = collect();
    parser.push("event: transition\ndata: partial");
    expect(messages).toHaveLength(0);
    parser.push("\n\n");
    expect(messages).toHaveLength(1);
  });
});

function streamResponse(chunks: string[], { close = true }: { close?: boolean } = {}): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const c of chunks) controller.enqueue(encoder.encode(c));
      if (close) controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { "content-type": "text/event-stream" } });
}

describe("connectSse", () => {
  afterEach(() => vi.useRealTimers());

  it("reconnects with backoff and sends Last-Event-ID of the last seen event", async () => {
    vi.useFakeTimers();
    const calls: Record<string, string>[] = [];
    const fetchImpl = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ ...(init?.headers as Record<string, string>) });
      if (calls.length === 1) return streamResponse(["id: 3\nevent: transition\ndata: {}\n\n"]); // then drops
      if (calls.length === 2) throw new TypeError("network down");
      return streamResponse(["id: 4\nevent: transition\ndata: {}\n\nevent: end\ndata: {}\n\n"], { close: false });
    });
    const events: string[] = [];
    const errors: number[] = [];
    const connection = connectSse({
      url: "http://api/stream",
      lastEventId: "2",
      getHeaders: async () => ({ Authorization: "Bearer t" }),
      fetchImpl: fetchImpl as unknown as typeof fetch,
      initialBackoffMs: 100,
      onError: (_e, delay) => errors.push(delay),
      onMessage: (m) => {
        events.push(`${m.event}:${m.id}`);
        if (m.event === "end") connection.close();
      },
    });
    await vi.advanceTimersByTimeAsync(0);
    expect(calls[0]["Last-Event-ID"]).toBe("2");
    expect(calls[0].Authorization).toBe("Bearer t");
    await vi.advanceTimersByTimeAsync(100); // first reconnect (fails)
    await vi.advanceTimersByTimeAsync(200); // second reconnect (succeeds)
    expect(calls).toHaveLength(3);
    expect(calls[1]["Last-Event-ID"]).toBe("3");
    expect(calls[2]["Last-Event-ID"]).toBe("3");
    expect(errors).toEqual([100, 200]);
    expect(events).toEqual(["transition:3", "transition:4", "end:4"]);
    expect(connection.lastEventId).toBe("4");
  });

  it("stops on fatal statuses", async () => {
    const onFatal = vi.fn();
    const fetchImpl = vi.fn(async () => new Response("", { status: 403 }));
    connectSse({ url: "http://api/stream", fetchImpl: fetchImpl as unknown as typeof fetch, onMessage: () => {}, onFatal });
    await vi.waitFor(() => expect(onFatal).toHaveBeenCalledWith(403));
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it("caps the exponential backoff", () => {
    expect(backoffDelay(0, 500, 15000)).toBe(500);
    expect(backoffDelay(3, 500, 15000)).toBe(4000);
    expect(backoffDelay(10, 500, 15000)).toBe(15000);
  });
});
