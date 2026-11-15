import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setSessionHandlers } from "@/lib/auth/session";
import { ApiError, toApiError } from "./api-error";
import { apiFetch } from "./client";

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/problem+json", ...headers } });
}

describe("apiFetch", () => {
  const onUnauthorized = vi.fn();
  beforeEach(() => {
    setSessionHandlers({ getAccessToken: async () => "token-123", onUnauthorized });
  });
  afterEach(() => {
    setSessionHandlers(null);
    vi.unstubAllGlobals();
    onUnauthorized.mockReset();
  });

  it("sends bearer token, correlation id, JSON body and idempotency key", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
    const result = await apiFetch<{ ok: boolean }>("/api/v1/x", { method: "POST", body: { a: 1 }, idempotencyKey: "k-1", query: { page: 0, q: "" } });
    expect(result).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/x?page=0");
    const headers = init.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer token-123");
    expect(headers["X-Correlation-Id"]).toMatch(/[0-9a-f-]{8,}/);
    expect(headers["Idempotency-Key"]).toBe("k-1");
    expect(headers["Content-Type"]).toBe("application/json");
    expect(init.body).toBe('{"a":1}');
  });

  it("parses RFC 9457 problem details with validation errors and extensions", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        jsonResponse(
          422,
          {
            type: "https://workflowengine.io/problems/invalid-workflow",
            title: "Invalid workflow",
            status: 422,
            detail: "The workflow definition is invalid (1 error(s))",
            instance: "/api/v1/workflows/1",
            errors: [{ code: "UNREACHABLE_NODE", message: "Node is not reachable", nodeId: "n2", edgeId: null }],
          },
          { "X-Correlation-Id": "corr-1, corr-1" },
        ),
      ),
    );
    const error = await apiFetch("/api/v1/workflows/1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(422);
    expect(apiError.problem).toBe("invalid-workflow");
    expect(apiError.title).toBe("Invalid workflow");
    expect(apiError.errors).toEqual([{ code: "UNREACHABLE_NODE", message: "Node is not reachable", nodeId: "n2", edgeId: null }]);
    expect(apiError.correlationId).toBe("corr-1");
  });

  it("keeps extension members such as currentRevision on 409", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(409, { type: "https://x/problems/revision-conflict", title: "Concurrent modification", status: 409, currentRevision: 4 })),
    );
    const error = (await apiFetch("/x").catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(409);
    expect(error.extensions.currentRevision).toBe(4);
  });

  it("notifies the session on 401", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 401 })));
    const error = (await apiFetch("/x").catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(401);
    expect(error.title).toBe("Session expired");
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("maps network failures to status 0 and returns undefined for 204", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new TypeError("Failed to fetch"))));
    const error = (await apiFetch("/x").catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(0);
    expect(error.correlationId).toBeTruthy();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 204 })));
    await expect(apiFetch("/x", { method: "DELETE" })).resolves.toBeUndefined();
  });

  it("handles non-JSON error bodies", () => {
    const error = toApiError(502, "<html>Bad gateway</html>", "c");
    expect(error.status).toBe(502);
    expect(error.title).toBe("Server error");
    expect(error.detail).toContain("Bad gateway");
  });
});
