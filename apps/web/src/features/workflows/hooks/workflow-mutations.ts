/** Workflow mutations (create, duplicate, archive, save, validate, publish, secrets, sharing) and their cache updates. */
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/endpoints";
import { queryKeys } from "@/lib/api/query-keys";
import type { Workflow, WorkflowDefinition, WorkflowRole } from "@/lib/api/types";

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
