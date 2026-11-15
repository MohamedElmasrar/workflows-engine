/**
 * Parser for the WHATWG event-stream format (Server-Sent Events): `id:`, `event:`, `data:` (multi-line,
 * joined with "\n"), `retry:` and `:` comments; events are dispatched on a blank line.
 */

export interface SseMessage {
  /** Value of the last `id:` field seen (persists across events, as in the spec). */
  id: string | null;
  event: string;
  data: string;
  retry?: number;
}

export interface SseParser {
  push: (chunk: string) => void;
  /** Drops any partially received event (e.g. when the connection is reset). */
  reset: () => void;
}

export function createSseParser(onMessage: (message: SseMessage) => void): SseParser {
  let buffer = "";
  let data: string[] = [];
  let eventType = "";
  let lastEventId: string | null = null;
  let retry: number | undefined;
  let sawField = false;

  const dispatch = () => {
    if (data.length > 0 || eventType) {
      onMessage({ id: lastEventId, event: eventType || "message", data: data.join("\n"), retry });
    }
    data = [];
    eventType = "";
    retry = undefined;
    sawField = false;
  };

  const processLine = (line: string) => {
    if (line === "") {
      if (sawField) dispatch();
      return;
    }
    if (line.startsWith(":")) return; // comment / keep-alive
    const colon = line.indexOf(":");
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? "" : line.slice(colon + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    switch (field) {
      case "data":
        data.push(value);
        sawField = true;
        break;
      case "event":
        eventType = value;
        sawField = true;
        break;
      case "id":
        if (!value.includes("\u0000")) lastEventId = value;
        sawField = true;
        break;
      case "retry":
        if (/^\d+$/.test(value)) retry = Number(value);
        sawField = true;
        break;
      default:
        break; // unknown fields are ignored
    }
  };

  return {
    push(chunk: string) {
      buffer += chunk;
      // Split on CRLF, LF or CR. A trailing CR may be the first half of a CRLF split across
      // chunks, so keep it in the buffer until more data arrives.
      let start = 0;
      for (let i = 0; i < buffer.length; i++) {
        const ch = buffer[i];
        if (ch === "\n" || ch === "\r") {
          if (ch === "\r" && i === buffer.length - 1) break;
          processLine(buffer.slice(start, i));
          if (ch === "\r" && buffer[i + 1] === "\n") i++;
          start = i + 1;
        }
      }
      buffer = buffer.slice(start);
    },
    reset() {
      buffer = "";
      data = [];
      eventType = "";
      retry = undefined;
      sawField = false;
    },
  };
}
