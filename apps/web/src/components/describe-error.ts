/** Turns any thrown value (usually an `ApiError`) into a title, detail and correlation id for the UI. */
import { isApiError } from "@/lib/api/api-error";

/** Human-readable explanation per status, used by error states and toasts. */
export function describeError(error: unknown): { title: string; detail: string; correlationId: string | null } {
  if (isApiError(error)) {
    const fallback: Record<number, string> = {
      0: "The API could not be reached.",
      403: "You do not have permission to perform this action.",
      404: "The resource does not exist or is not shared with you.",
      409: "The resource changed in the meantime.",
      422: "The request was rejected by validation.",
    };
    return {
      title: error.title,
      detail: error.detail ?? fallback[error.status] ?? "Something went wrong.",
      correlationId: error.correlationId,
    };
  }
  return {
    title: "Unexpected error",
    detail: error instanceof Error ? error.message : "Something went wrong.",
    correlationId: null,
  };
}
