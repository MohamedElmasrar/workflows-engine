"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/endpoints";
import { ApiError, isApiError } from "@/lib/api/client";
import { queryKeys, type WorkflowListParams } from "@/lib/api/query-keys";
import type { Execution, Workflow, WorkflowDefinition, WorkflowRole } from "@/lib/api/types";

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

function useInvalidateWorkflowLists() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ["workflows", "list"] });
    void qc.invalidateQueries({ queryKey: queryKeys.workflows.stats });
  };
}

export function useCreateWorkflow() {
  const invalidate = useInvalidateWorkflowLists();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.createWorkflow,
    onSuccess: (workflow) => {
      qc.setQueryData(queryKeys.workflows.detail(workflow.id), workflow);
      invalidate();
    },
  });
}

export function useDuplicateWorkflow() {
  const invalidate = useInvalidateWorkflowLists();
  return useMutation({ mutationFn: api.duplicateWorkflow, onSuccess: invalidate });
}

export function useArchiveWorkflow() {
  const invalidate = useInvalidateWorkflowLists();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.archiveWorkflow,
    onSuccess: (_data, id) => {
      invalidate();
      void qc.invalidateQueries({ queryKey: queryKeys.workflows.detail(id) });
    },
  });
}

export function useSaveWorkflow(id: string) {
  const qc = useQueryClient();
  const invalidate = useInvalidateWorkflowLists();
  return useMutation({
    mutationFn: (body: { name: string; description?: string | null; definition: WorkflowDefinition; revision: number }) =>
      api.updateWorkflow(id, body),
    onSuccess: (workflow) => {
      qc.setQueryData<Workflow>(queryKeys.workflows.detail(id), workflow);
      invalidate();
    },
  });
}

export function useValidateWorkflow(id: string) {
  return useMutation({ mutationFn: (definition: WorkflowDefinition) => api.validateWorkflow(id, definition) });
}

export function usePublishWorkflow(id: string) {
  const qc = useQueryClient();
  const invalidate = useInvalidateWorkflowLists();
  return useMutation({
    mutationFn: () => api.publishWorkflow(id),
    onSuccess: async () => {
      invalidate();
      await qc.invalidateQueries({ queryKey: queryKeys.workflows.detail(id) });
    },
  });
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Starts an execution. Right after publishing, the engine may not have received the version
 * yet (409 version-not-available), so that case is retried with backoff using the same
 * Idempotency-Key, which makes the retries safe.
 */
export async function startExecutionWithRetry(
  workflowId: string,
  body: { input?: unknown; version?: number },
  idempotencyKey: string,
  { attempts = 6, initialDelayMs = 500 }: { attempts?: number; initialDelayMs?: number } = {},
): Promise<Execution> {
  let lastError: unknown;
  for (let attempt = 0; attempt < attempts; attempt++) {
    try {
      return await api.startExecution(workflowId, body, idempotencyKey);
    } catch (error) {
      lastError = error;
      const retryable = isApiError(error) && error.status === 409 && error.problem === "version-not-available";
      if (!retryable || attempt === attempts - 1) throw error;
      await sleep(initialDelayMs * 2 ** attempt);
    }
  }
  throw lastError instanceof ApiError ? lastError : new Error("Could not start the execution");
}

export function useStartExecution(workflowId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: { input: unknown; idempotencyKey: string }) =>
      startExecutionWithRetry(workflowId, { input }, idempotencyKey),
    onSuccess: (execution) => {
      qc.setQueryData(queryKeys.executions.detail(execution.id), execution);
      void qc.invalidateQueries({ queryKey: queryKeys.executions.all });
    },
  });
}

export function useRotateSecret(id: string) {
  return useMutation({ mutationFn: () => api.rotateWebhookSecret(id) });
}

export function useGrantPermission(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ username, role }: { username: string; role: Exclude<WorkflowRole, "OWNER"> }) => api.grantPermission(id, username, role),
    onSuccess: (list) => qc.setQueryData(queryKeys.workflows.permissions(id), list),
  });
}

export function useRevokePermission(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (username: string) => api.revokePermission(id, username),
    onSuccess: (list) => qc.setQueryData(queryKeys.workflows.permissions(id), list),
  });
}
