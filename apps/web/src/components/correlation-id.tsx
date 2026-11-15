"use client";
/** Shows a request's correlation id with a copy button (helps find the request in the logs). */
import { CopyButton } from "./copy-button";

export function CorrelationId({ id }: { id: string | null }) {
  if (!id) return null;
  return (
    <span className="inline-flex items-center gap-1 font-mono text-xs text-muted-foreground">
      Correlation ID: {id}
      <CopyButton value={id} label="Copy correlation ID" size="icon-xs" />
    </span>
  );
}
