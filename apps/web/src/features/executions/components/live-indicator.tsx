"use client";
/** Small status label for the SSE connection (connecting, live, reconnecting, unavailable). */
import { Loader2, Radio, WifiOff } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import type { LiveState } from "../hooks/use-live-execution";

export function LiveIndicator({ state }: { state: LiveState }) {
  const map: Record<LiveState, { label: string; className: string; icon: ReactNode }> = {
    idle: { label: "", className: "", icon: null },
    connecting: { label: "Connecting…", className: "text-muted-foreground", icon: <Loader2 className="size-3 animate-spin" /> },
    live: { label: "Live", className: "text-emerald-700", icon: <Radio className="size-3 animate-pulse" /> },
    reconnecting: { label: "Reconnecting…", className: "text-amber-700", icon: <WifiOff className="size-3" /> },
    ended: { label: "", className: "", icon: null },
    error: { label: "Live updates unavailable", className: "text-destructive", icon: <WifiOff className="size-3" /> },
  };
  const item = map[state];
  if (!item.label) return null;
  return (
    <span className={cn("flex items-center gap-1 text-xs", item.className)} role="status" data-testid="live-indicator">
      {item.icon}
      {item.label}
    </span>
  );
}
