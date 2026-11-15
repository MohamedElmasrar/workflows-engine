/** React Query keys and list parameter types, kept in one place so invalidation stays consistent. */
export interface WorkflowListParams {
  search?: string;
  status?: string;
  sort?: string;
  direction?: string;
  page?: number;
  size?: number;
}

export interface ExecutionListParams {
  workflowId?: string;
  status?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const queryKeys = {
  me: ["me"] as const,
  workflows: {
    all: ["workflows"] as const,
    list: (params: WorkflowListParams) => ["workflows", "list", params] as const,
    stats: ["workflows", "stats"] as const,
    detail: (id: string) => ["workflows", "detail", id] as const,
    versions: (id: string) => ["workflows", "detail", id, "versions"] as const,
    version: (id: string, version: number) => ["workflows", "detail", id, "versions", version] as const,
    permissions: (id: string) => ["workflows", "detail", id, "permissions"] as const,
    triggers: (id: string) => ["workflows", "detail", id, "triggers"] as const,
    deliveries: (id: string) => ["workflows", "detail", id, "deliveries"] as const,
  },
  executions: {
    all: ["executions"] as const,
    list: (params: ExecutionListParams) => ["executions", "list", params] as const,
    stats: ["executions", "stats"] as const,
    detail: (id: string) => ["executions", "detail", id] as const,
    events: (id: string) => ["executions", "detail", id, "events"] as const,
    latestForWorkflow: (workflowId: string) => ["executions", "latest", workflowId] as const,
  },
};
