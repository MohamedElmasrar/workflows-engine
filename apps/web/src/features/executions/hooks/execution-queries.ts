/** Execution read queries: paged list, dashboard stats, single execution and the latest run of a workflow. */
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/endpoints";
import { type ExecutionListParams, queryKeys } from "@/lib/api/query-keys";
import { isTerminalExecution } from "@/lib/api/types";

export function useExecutionList(params: ExecutionListParams) {
  return useQuery({
    queryKey: queryKeys.executions.list(params),
    queryFn: ({ signal }) => api.listExecutions(params, signal),
    placeholderData: keepPreviousData,
  });
}

export function useExecutionStats() {
  return useQuery({ queryKey: queryKeys.executions.stats, queryFn: api.executionStats, refetchInterval: 15_000 });
}

export function useExecution(id: string | null, options: { refetchWhileActive?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.executions.detail(id ?? ""),
    queryFn: ({ signal }) => api.getExecution(id as string, signal),
    enabled: Boolean(id),
    refetchInterval: (query) =>
      options.refetchWhileActive && query.state.data && !isTerminalExecution(query.state.data.status) ? 3000 : false,
  });
}

/** Latest execution of a workflow (for the editor's "Show last run" overlay). */
export function useLatestExecution(workflowId: string, enabled: boolean) {
  const latest = useQuery({
    queryKey: queryKeys.executions.latestForWorkflow(workflowId),
    queryFn: async () => (await api.listExecutions({ workflowId, size: 1, page: 0 })).items[0] ?? null,
    enabled,
  });
  const detail = useExecution(enabled ? (latest.data?.id ?? null) : null, { refetchWhileActive: true });
  return { latest, detail };
}
