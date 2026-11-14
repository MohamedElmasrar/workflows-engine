"use client";

import { useSyncExternalStore } from "react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatAbsolute, formatRelative } from "@/lib/format";

// One shared 15 s ticker for every relative timestamp on screen.
const listeners = new Set<() => void>();
let now = Date.now();
let interval: ReturnType<typeof setInterval> | null = null;

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (!interval) {
    const tick = () => {
      now = Date.now();
      listeners.forEach((l) => l());
    };
    interval = setInterval(tick, 15_000);
    queueMicrotask(tick);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && interval) {
      clearInterval(interval);
      interval = null;
    }
  };
}

export function useNow(): number {
  return useSyncExternalStore(
    subscribe,
    () => now,
    () => now,
  );
}

export function RelativeTime({ value, className }: { value: string | null | undefined; className?: string }) {
  const current = useNow();
  if (!value) return <span className={className}>—</span>;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <time dateTime={value} className={className} tabIndex={0}>
          {formatRelative(value, current)}
        </time>
      </TooltipTrigger>
      <TooltipContent>{formatAbsolute(value)}</TooltipContent>
    </Tooltip>
  );
}
