/** Executions, node runs, attempts and the persisted history (timeline). */
import type { WorkflowDefinition } from "./definition";

export const EXECUTION_STATUSES = ["PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT"] as const;
export type ExecutionStatus = (typeof EXECUTION_STATUSES)[number];

export const NODE_STATUSES = [
  "PENDING",
  "READY",
  "QUEUED",
  "RUNNING",
  "COMPLETED",
  "FAILED",
  "SKIPPED",
  "CANCELLED",
  "TIMED_OUT",
] as const;
export type NodeStatus = (typeof NODE_STATUSES)[number];

export type ExecutionTriggerType = "MANUAL" | "WEBHOOK" | "SCHEDULE" | "RETRY";

export interface NodeError {
  code: string;
  message: string;
  retryable: boolean;
}

export type AttemptStatus = "DISPATCHED" | "RUNNING" | "COMPLETED" | "FAILED" | "LEASE_EXPIRED" | "CANCELLED";

export interface Attempt {
  attemptId: string;
  number: number;
  status: AttemptStatus;
  workerId: string | null;
  dispatchedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface NodeExecution {
  nodeId: string;
  nodeType: string;
  name: string;
  status: NodeStatus;
  attemptCount: number;
  input: unknown;
  output: unknown;
  branch: string | null;
  error: NodeError | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  nextAttemptAt: string | null;
  resumeAt: string | null;
  attempts: Attempt[];
}

export interface ExecutionSummary {
  id: string;
  workflowId: string;
  workflowName: string;
  workflowVersion: number;
  status: ExecutionStatus;
  triggerType: ExecutionTriggerType;
  triggeredBy: string;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  retryOf: string | null;
}

export interface Execution extends ExecutionSummary {
  input: unknown;
  output: unknown | null;
  deadlineAt: string | null;
  lastEventSequence: number;
  definition: WorkflowDefinition;
  nodes: NodeExecution[];
}

export interface ExecutionStats {
  executionsToday: number;
  completedToday: number;
  failedToday: number;
  running: number;
  pending: number;
  recent: ExecutionSummary[];
}

export type HistoryEntryType =
  | "EXECUTION_CREATED"
  | "EXECUTION_STATUS_CHANGED"
  | "NODE_STATUS_CHANGED"
  | "NODE_RETRY_SCHEDULED"
  | "STALE_RESULT_IGNORED";

export interface HistoryEntry {
  sequence: number;
  type: HistoryEntryType;
  nodeId: string | null;
  attemptId: string | null;
  status: string | null;
  details: Record<string, unknown>;
  occurredAt: string;
}

export const TERMINAL_EXECUTION_STATUSES: readonly ExecutionStatus[] = ["COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT"];

export function isTerminalExecution(status: ExecutionStatus): boolean {
  return TERMINAL_EXECUTION_STATUSES.includes(status);
}
