/** Starts an execution, retrying the 409 "version not available yet" case right after a publish. */
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, isApiError } from "@/lib/api/api-error";
import { api } from "@/lib/api/endpoints";
import { queryKeys } from "@/lib/api/query-keys";
import type { Execution } from "@/lib/api/types";

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
