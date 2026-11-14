/** Domain types mirroring docs/api/README.md and the workflow definition JSON schema (v1). */

export const NODE_TYPES = [
  "MANUAL_TRIGGER",
  "WEBHOOK_TRIGGER",
  "SCHEDULE_TRIGGER",
  "HTTP_REQUEST",
  "TRANSFORM",
  "LOG",
  "NOTIFICATION",
  "CONDITION",
  "DELAY",
] as const;
export type NodeType = (typeof NODE_TYPES)[number];

export const TRIGGER_TYPES = ["MANUAL_TRIGGER", "WEBHOOK_TRIGGER", "SCHEDULE_TRIGGER"] as const satisfies readonly NodeType[];
export type TriggerNodeType = (typeof TRIGGER_TYPES)[number];

export function isTriggerType(type: string): type is TriggerNodeType {
  return (TRIGGER_TYPES as readonly string[]).includes(type);
}

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type JsonObject = { [key: string]: JsonValue };

export interface RetryPolicy {
  maxAttempts: number;
  initialIntervalMs?: number;
  multiplier?: number;
  maxIntervalMs?: number;
}

export interface Position {
  x: number;
  y: number;
}

export interface DefinitionNode {
  id: string;
  type: NodeType;
  name: string;
  position?: Position;
  config: Record<string, unknown>;
  timeoutSeconds?: number;
  retryPolicy?: RetryPolicy;
}

export type BranchHandle = "true" | "false";

export interface DefinitionEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: BranchHandle | null;
}

export interface WorkflowSettings {
  executionTimeoutSeconds?: number;
  maxConcurrentExecutions?: number;
}

export interface WorkflowDefinition {
  schemaVersion: 1;
  nodes: DefinitionNode[];
  edges: DefinitionEdge[];
  settings?: WorkflowSettings;
}

export type WorkflowStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type WorkflowRole = "OWNER" | "EDITOR" | "VIEWER";

export interface Workflow {
  id: string;
  name: string;
  description: string | null;
  status: WorkflowStatus;
  ownerId: string;
  role: WorkflowRole;
  revision: number;
  latestVersion: number | null;
  hasUnpublishedChanges: boolean;
  definition: WorkflowDefinition;
  createdAt: string;
  updatedAt: string;
}

export type WorkflowSummary = Omit<Workflow, "definition" | "revision"> & {
  triggerType: string | null;
  nodeCount: number;
};

export interface WorkflowVersion {
  workflowId: string;
  version: number;
  name: string;
  publishedBy: string;
  publishedAt: string;
  definition: WorkflowDefinition | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Me {
  id: string;
  username: string;
  admin: boolean;
  roles: string[];
}

export interface WorkflowStats {
  totalWorkflows: number;
  activeWorkflows: number;
}

export interface ValidationIssue {
  code: string;
  message: string;
  nodeId: string | null;
  edgeId: string | null;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationIssue[];
}

export interface Permission {
  userId: string;
  username: string;
  role: WorkflowRole;
  grantedAt: string;
}

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

export interface WebhookTrigger {
  url: string | null;
  active: boolean;
  requireSignature: boolean;
  requiredFields: string[];
  version: number;
}

export interface ScheduleTrigger {
  cron: string;
  timezone: string;
  active: boolean;
  nextFireAt: string | null;
  lastFiredAt: string | null;
  version: number;
}

export interface WorkflowTriggers {
  webhook: WebhookTrigger | null;
  schedule: ScheduleTrigger | null;
}

/** Shape observed from GET /api/v1/workflows/{id}/trigger-deliveries (not documented in detail). */
export interface TriggerDelivery {
  id: string;
  workflowId: string;
  triggerType: "WEBHOOK" | "SCHEDULE" | string;
  dedupeKey: string | null;
  executionId: string | null;
  executionStatus: string | null;
  payloadBytes: number | null;
  receivedAt: string;
}

export const TERMINAL_EXECUTION_STATUSES: readonly ExecutionStatus[] = ["COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT"];

export function isTerminalExecution(status: ExecutionStatus): boolean {
  return TERMINAL_EXECUTION_STATUSES.includes(status);
}
