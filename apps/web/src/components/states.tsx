"use client";

import { AlertTriangle, Inbox, Loader2, RefreshCw } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import { CopyButton } from "./copy-button";

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed p-8 text-center", className)}>
      <div className="flex size-10 items-center justify-center rounded-full bg-muted text-muted-foreground" aria-hidden>
        {icon ?? <Inbox className="size-5" />}
      </div>
      <p className="font-medium">{title}</p>
      {description ? <p className="max-w-sm text-sm text-muted-foreground">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}

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

export function CorrelationId({ id }: { id: string | null }) {
  if (!id) return null;
  return (
    <span className="inline-flex items-center gap-1 font-mono text-xs text-muted-foreground">
      Correlation ID: {id}
      <CopyButton value={id} label="Copy correlation ID" size="icon-xs" />
    </span>
  );
}

export function ErrorState({
  error,
  onRetry,
  className,
  title,
}: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
  title?: string;
}) {
  const info = describeError(error);
  return (
    <div
      role="alert"
      className={cn("flex flex-col items-center justify-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-6 text-center", className)}
    >
      <AlertTriangle className="size-6 text-destructive" aria-hidden />
      <p className="font-medium">{title ?? info.title}</p>
      <p className="max-w-md text-sm text-muted-foreground">{info.detail}</p>
      <CorrelationId id={info.correlationId} />
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry} className="mt-1">
          <RefreshCw /> Try again
        </Button>
      ) : null}
    </div>
  );
}

export function LoadingState({ label = "Loading…", className }: { label?: string; className?: string }) {
  return (
    <div className={cn("flex items-center justify-center gap-2 p-8 text-sm text-muted-foreground", className)} role="status">
      <Loader2 className="size-4 animate-spin" aria-hidden /> {label}
    </div>
  );
}

export function TableSkeleton({ rows = 5, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div className="space-y-2 p-4" role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="flex gap-4">
          {Array.from({ length: columns }, (_, c) => (
            <Skeleton key={c} className="h-6 flex-1" />
          ))}
        </div>
      ))}
    </div>
  );
}
