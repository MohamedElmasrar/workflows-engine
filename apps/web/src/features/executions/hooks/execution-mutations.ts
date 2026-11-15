/** Cancel and retry mutations; both update the cached execution and refresh the lists. */
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/endpoints";
import { queryKeys } from "@/lib/api/query-keys";

export function useCancelExecution(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.cancelExecution(id),
    onSuccess: (execution) => {
      qc.setQueryData(queryKeys.executions.detail(id), execution);
      void qc.invalidateQueries({ queryKey: ["executions", "list"] });
    },
  });
}

export function useRetryExecution(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.retryExecution(id),
    onSuccess: (execution) => {
      qc.setQueryData(queryKeys.executions.detail(execution.id), execution);
      void qc.invalidateQueries({ queryKey: ["executions", "list"] });
    },
  });
}
