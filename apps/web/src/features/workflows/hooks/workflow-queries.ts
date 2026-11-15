/** Workflow read queries: list, stats, detail, versions, permissions, triggers and webhook deliveries. */
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/endpoints";
import { queryKeys, type WorkflowListParams } from "@/lib/api/query-keys";

export function useWorkflowList(params: WorkflowListParams) {
  return useQuery({
    queryKey: queryKeys.workflows.list(params),
    queryFn: ({ signal }) => api.listWorkflows(params, signal),
    placeholderData: keepPreviousData,
  });
}

export function useWorkflowStats() {
  return useQuery({ queryKey: queryKeys.workflows.stats, queryFn: api.workflowStats });
}

export function useWorkflow(id: string) {
  return useQuery({
    queryKey: queryKeys.workflows.detail(id),
    queryFn: ({ signal }) => api.getWorkflow(id, signal),
    // The editor owns the draft; avoid background refetches replacing what the user sees.
    staleTime: Infinity,
  });
}

export function useWorkflowVersions(id: string, enabled = true) {
  return useQuery({ queryKey: queryKeys.workflows.versions(id), queryFn: () => api.listVersions(id), enabled });
}

export function useWorkflowVersion(id: string, version: number | null) {
  return useQuery({
    queryKey: queryKeys.workflows.version(id, version ?? -1),
    queryFn: () => api.getVersion(id, version as number),
    enabled: version !== null,
    staleTime: Infinity, // versions are immutable
  });
}

export function usePermissions(id: string, enabled = true) {
  return useQuery({ queryKey: queryKeys.workflows.permissions(id), queryFn: () => api.listPermissions(id), enabled });
}

export function useTriggers(id: string, enabled = true) {
  return useQuery({ queryKey: queryKeys.workflows.triggers(id), queryFn: () => api.getTriggers(id), enabled });
}

export function useDeliveries(id: string, enabled = true) {
  return useQuery({ queryKey: queryKeys.workflows.deliveries(id), queryFn: () => api.listDeliveries(id, 20), enabled });
}
