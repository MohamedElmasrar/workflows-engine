/** `apiFetch`: the single entry point for REST calls (auth header, correlation id, JSON, errors). */
import { env } from "@/lib/env";
import { getAccessToken, notifyUnauthorized } from "@/lib/auth/session";
import { ApiError, toApiError } from "./api-error";

export function newCorrelationId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  // Fallback for non-secure contexts; correlation ids are not security sensitive.
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
}

export type QueryValue = string | number | boolean | null | undefined;

export interface ApiFetchOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** JSON-serialisable request body. */
  body?: unknown;
  query?: Record<string, QueryValue>;
  headers?: Record<string, string>;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(path.startsWith("http") ? path : `${env.apiBaseUrl}${path}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

/**
 * Single entry point for REST calls: attaches the bearer token and an `X-Correlation-Id`,
 * serialises JSON, and turns non-2xx responses into `ApiError` (RFC 9457 aware).
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const correlationId = newCorrelationId();
  const headers: Record<string, string> = {
    Accept: "application/json, application/problem+json",
    "X-Correlation-Id": correlationId,
    ...options.headers,
  };
  const token = await getAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.idempotencyKey) headers["Idempotency-Key"] = options.idempotencyKey;
  if (options.body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new ApiError({
      status: 0,
      title: "Network error",
      detail: "The API could not be reached. Check your connection and that the gateway is running.",
      correlationId,
    });
  }

  const responseCorrelationId = response.headers.get("X-Correlation-Id")?.split(",")[0]?.trim() || correlationId;
  const text = response.status === 204 ? "" : await response.text();
  let body: unknown = undefined;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }

  if (!response.ok) {
    const error = toApiError(response.status, body, responseCorrelationId);
    if (response.status === 401) notifyUnauthorized();
    throw error;
  }
  return body as T;
}
