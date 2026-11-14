import { apiFetch } from "./client";
import type { ExecutionListParams, WorkflowListParams } from "./query-keys";
import type {
  Execution,
  ExecutionStats,
  ExecutionSummary,
  HistoryEntry,
  Me,
  Page,
  Permission,
  TriggerDelivery,
  ValidationResult,
  Workflow,
  WorkflowDefinition,
  WorkflowRole,
  WorkflowStats,
  WorkflowSummary,
  WorkflowTriggers,
  WorkflowVersion,
} from "./types";

const wf = (id: string) => `/api/v1/workflows/${encodeURIComponent(id)}`;
const ex = (id: string) => `/api/v1/executions/${encodeURIComponent(id)}`;

export const api = {
  me: () => apiFetch<Me>("/api/v1/me"),

  listWorkflows: (params: WorkflowListParams, signal?: AbortSignal) =>
    apiFetch<Page<WorkflowSummary>>("/api/v1/workflows", { query: { ...params }, signal }),
  workflowStats: () => apiFetch<WorkflowStats>("/api/v1/workflows/stats"),
  getWorkflow: (id: string, signal?: AbortSignal) => apiFetch<Workflow>(wf(id), { signal }),
  createWorkflow: (body: { name: string; description?: string; definition?: WorkflowDefinition }) =>
    apiFetch<Workflow>("/api/v1/workflows", { method: "POST", body }),
  updateWorkflow: (
    id: string,
    body: { name: string; description?: string | null; definition: WorkflowDefinition; revision: number },
  ) => apiFetch<Workflow>(wf(id), { method: "PUT", body }),
  archiveWorkflow: (id: string) => apiFetch<void>(wf(id), { method: "DELETE" }),
  duplicateWorkflow: (id: string) => apiFetch<Workflow>(`${wf(id)}/duplicate`, { method: "POST" }),
  validateWorkflow: (id: string, definition?: WorkflowDefinition) =>
    apiFetch<ValidationResult>(`${wf(id)}/validate`, {
      method: "POST",
      body: definition ? { definition } : {},
    }),
  publishWorkflow: (id: string) => apiFetch<WorkflowVersion>(`${wf(id)}/publish`, { method: "POST" }),
  listVersions: (id: string) => apiFetch<WorkflowVersion[]>(`${wf(id)}/versions`),
  getVersion: (id: string, version: number) => apiFetch<WorkflowVersion>(`${wf(id)}/versions/${version}`),
  listPermissions: (id: string) => apiFetch<Permission[]>(`${wf(id)}/permissions`),
  grantPermission: (id: string, username: string, role: Exclude<WorkflowRole, "OWNER">) =>
    apiFetch<Permission[]>(`${wf(id)}/permissions/${encodeURIComponent(username)}`, { method: "PUT", body: { role } }),
  revokePermission: (id: string, username: string) =>
    apiFetch<Permission[]>(`${wf(id)}/permissions/${encodeURIComponent(username)}`, { method: "DELETE" }),

  getTriggers: (id: string) => apiFetch<WorkflowTriggers>(`${wf(id)}/triggers`),
  rotateWebhookSecret: (id: string) =>
    apiFetch<{ secret: string }>(`${wf(id)}/triggers/webhook/secret`, { method: "POST" }),
  listDeliveries: (id: string, limit = 20) =>
    apiFetch<TriggerDelivery[]>(`${wf(id)}/trigger-deliveries`, { query: { limit } }),

  startExecution: (workflowId: string, body: { input?: unknown; version?: number }, idempotencyKey: string) =>
    apiFetch<Execution>(`${wf(workflowId)}/executions`, { method: "POST", body, idempotencyKey }),
  listExecutions: (params: ExecutionListParams, signal?: AbortSignal) =>
    apiFetch<Page<ExecutionSummary>>("/api/v1/executions", { query: { ...params }, signal }),
  executionStats: () => apiFetch<ExecutionStats>("/api/v1/executions/stats"),
  getExecution: (id: string, signal?: AbortSignal) => apiFetch<Execution>(ex(id), { signal }),
  cancelExecution: (id: string) => apiFetch<Execution>(`${ex(id)}/cancel`, { method: "POST" }),
  retryExecution: (id: string) => apiFetch<Execution>(`${ex(id)}/retry`, { method: "POST" }),
  executionEvents: (id: string, after?: number, limit = 1000) =>
    apiFetch<HistoryEntry[]>(`${ex(id)}/events`, { query: { after, limit } }),
};
