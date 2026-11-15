/** Reads a nested react-hook-form error message by dotted path. */
import type { FieldError, FieldErrors } from "react-hook-form";

// RHF's `get` is not exported in a typed way.
export function errorAt(errors: FieldErrors, path: string): string | undefined {
  let current: unknown = errors;
  for (const part of path.split(".")) {
    if (typeof current !== "object" || current === null) return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  if (typeof current === "object" && current !== null) {
    const err = current as Partial<FieldError> & { root?: FieldError };
    if (typeof err.message === "string") return err.message;
    if (err.root && typeof err.root.message === "string") return err.root.message;
  }
  return undefined;
}
