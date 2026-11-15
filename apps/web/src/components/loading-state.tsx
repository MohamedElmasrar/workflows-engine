"use client";
/** Spinner with a label for loading content. */
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function LoadingState({ label = "Loading…", className }: { label?: string; className?: string }) {
  return (
    <div className={cn("flex items-center justify-center gap-2 p-8 text-sm text-muted-foreground", className)} role="status">
      <Loader2 className="size-4 animate-spin" aria-hidden /> {label}
    </div>
  );
}
