/** `ApiError`: RFC 9457 problem details normalised for the UI, and parsing of error responses. */
import type { ValidationIssue } from "./types";

/** RFC 9457 problem details, normalised for the UI. */
export class ApiError extends Error {
  readonly status: number;
  readonly type: string;
  readonly title: string;
  readonly detail: string | null;
  readonly errors: ValidationIssue[] | undefined;
  readonly correlationId: string | null;
  /** Additional problem members (e.g. `currentRevision`, `retryable`). */
  readonly extensions: Record<string, unknown>;

  constructor(init: {
    status: number;
    type?: string;
    title?: string;
    detail?: string | null;
    errors?: ValidationIssue[];
    correlationId?: string | null;
    extensions?: Record<string, unknown>;
  }) {
    super(init.detail || init.title || `HTTP ${init.status}`);
    this.name = "ApiError";
    this.status = init.status;
    this.type = init.type ?? "about:blank";
    this.title = init.title ?? defaultTitle(init.status);
    this.detail = init.detail ?? null;
    this.errors = init.errors;
    this.correlationId = init.correlationId ?? null;
    this.extensions = init.extensions ?? {};
  }

  /** Last path segment of the problem type, e.g. `revision-conflict`. */
  get problem(): string {
    const parts = this.type.split("/");
    return parts[parts.length - 1] ?? this.type;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

function defaultTitle(status: number): string {
  switch (status) {
    case 0:
      return "Network error";
    case 400:
      return "Bad request";
    case 401:
      return "Session expired";
    case 403:
      return "Access denied";
    case 404:
      return "Not found";
    case 409:
      return "Conflict";
    case 413:
      return "Payload too large";
    case 422:
      return "Validation failed";
    case 429:
      return "Too many requests";
    default:
      return status >= 500 ? "Server error" : `HTTP ${status}`;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseIssues(value: unknown): ValidationIssue[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.filter(isRecord).map((e) => ({
    code: typeof e.code === "string" ? e.code : "ERROR",
    message: typeof e.message === "string" ? e.message : String(e.message ?? ""),
    nodeId: typeof e.nodeId === "string" ? e.nodeId : null,
    edgeId: typeof e.edgeId === "string" ? e.edgeId : null,
  }));
}

const KNOWN_PROBLEM_MEMBERS = new Set(["type", "title", "status", "detail", "instance", "errors"]);

/** Builds an ApiError from a status, an optional body (parsed JSON or text) and the correlation id. */
export function toApiError(status: number, body: unknown, correlationId: string | null): ApiError {
  if (isRecord(body)) {
    const extensions: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(body)) {
      if (!KNOWN_PROBLEM_MEMBERS.has(key)) extensions[key] = value;
    }
    return new ApiError({
      status: typeof body.status === "number" ? body.status : status,
      type: typeof body.type === "string" ? body.type : undefined,
      title: typeof body.title === "string" ? body.title : typeof body.error === "string" ? body.error : undefined,
      detail: typeof body.detail === "string" ? body.detail : typeof body.message === "string" ? body.message : null,
      errors: parseIssues(body.errors),
      correlationId,
      extensions,
    });
  }
  return new ApiError({
    status,
    detail: typeof body === "string" && body.trim() ? body.trim().slice(0, 500) : null,
    correlationId,
  });
}
