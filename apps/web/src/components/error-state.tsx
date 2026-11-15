"use client";
/** Error box with the problem details, correlation id and an optional retry button. */
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { CorrelationId } from "./correlation-id";
import { describeError } from "./describe-error";

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
