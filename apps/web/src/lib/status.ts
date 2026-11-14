import type { ExecutionStatus, NodeStatus, WorkflowStatus } from "@/lib/api/types";

type Tone = "neutral" | "amber" | "blue" | "green" | "red" | "muted";

export const toneClasses: Record<Tone, string> = {
  neutral: "bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-200",
  amber: "bg-amber-50 text-amber-800 border-amber-200 dark:bg-amber-950 dark:text-amber-200",
  blue: "bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950 dark:text-blue-200",
  green: "bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950 dark:text-emerald-200",
  red: "bg-red-50 text-red-700 border-red-200 dark:bg-red-950 dark:text-red-200",
  muted: "bg-muted text-muted-foreground border-border",
};

export const executionStatusTone: Record<ExecutionStatus, Tone> = {
  PENDING: "neutral",
  RUNNING: "blue",
  COMPLETED: "green",
  FAILED: "red",
  CANCELLED: "muted",
  TIMED_OUT: "red",
};

export const nodeStatusTone: Record<NodeStatus, Tone> = {
  PENDING: "neutral",
  READY: "amber",
  QUEUED: "amber",
  RUNNING: "blue",
  COMPLETED: "green",
  FAILED: "red",
  TIMED_OUT: "red",
  SKIPPED: "muted",
  CANCELLED: "muted",
};

export const workflowStatusTone: Record<WorkflowStatus, Tone> = {
  DRAFT: "neutral",
  ACTIVE: "green",
  ARCHIVED: "muted",
};

/** Ring/border classes for graph nodes coloured by node status. */
export const nodeStatusRing: Record<NodeStatus, string> = {
  PENDING: "ring-2 ring-slate-300",
  READY: "ring-2 ring-amber-400",
  QUEUED: "ring-2 ring-amber-400",
  RUNNING: "ring-2 ring-blue-500 animate-pulse",
  COMPLETED: "ring-2 ring-emerald-500",
  FAILED: "ring-2 ring-red-500",
  TIMED_OUT: "ring-2 ring-red-500",
  SKIPPED: "opacity-60 border-dashed",
  CANCELLED: "opacity-60",
};
