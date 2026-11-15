/** Workflow definition model (mirrors workflow-definition.v1.json): nodes, edges, settings. */

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
