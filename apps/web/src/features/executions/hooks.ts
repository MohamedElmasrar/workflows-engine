"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { buildUrl } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import { queryKeys, type ExecutionListParams } from "@/lib/api/query-keys";
import { isTerminalExecution, type Execution, type HistoryEntry } from "@/lib/api/types";
import { getAccessToken, notifyUnauthorized } from "@/lib/auth/session";
import { connectSse } from "@/lib/sse/connect";

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

function mergeEntries(existing: HistoryEntry[] | undefined, incoming: HistoryEntry[]): HistoryEntry[] {
  const bySequence = new Map<number, HistoryEntry>();
  for (const entry of existing ?? []) bySequence.set(entry.sequence, entry);
  for (const entry of incoming) bySequence.set(entry.sequence, entry);
  return [...bySequence.values()].sort((a, b) => a.sequence - b.sequence);
}

export type LiveState = "connecting" | "live" | "reconnecting" | "ended" | "idle" | "error";

/**
 * Loads the execution and its history, then follows `/events/stream?after=<last>` until the
 * `end` event. Each transition is appended to the cached timeline and triggers a throttled
 * (500 ms) refetch of the execution so node states, attempts and outputs stay current.
 */
export function useLiveExecution(id: string) {
  const qc = useQueryClient();
  const execution = useExecution(id);
  const events = useQuery({
    queryKey: queryKeys.executions.events(id),
    queryFn: () => api.executionEvents(id, undefined, 1000),
    staleTime: Infinity,
  });
  const [liveState, setLiveState] = useState<LiveState>("idle");
  const throttle = useRef<{ timer: ReturnType<typeof setTimeout> | null; last: number }>({ timer: null, last: 0 });

  const eventsLoaded = events.isSuccess;
  const status = execution.data?.status;
  const lastEventSequence = execution.data?.lastEventSequence ?? 0;
  const loadedLastSequence = events.data?.length ? events.data[events.data.length - 1].sequence : 0;
  // Nothing to stream: the execution finished and every transition is already loaded.
  const alreadyComplete = Boolean(eventsLoaded && status && isTerminalExecution(status) && loadedLastSequence >= lastEventSequence);
  const shouldStream = Boolean(eventsLoaded && status) && !alreadyComplete;

  useEffect(() => {
    if (!shouldStream) return;
    const loaded = qc.getQueryData<HistoryEntry[]>(queryKeys.executions.events(id)) ?? [];
    const lastSequence = loaded.length ? loaded[loaded.length - 1].sequence : 0;

    const state = throttle.current;
    const refreshExecution = () => {
      const now = Date.now();
      const run = () => {
        state.timer = null;
        state.last = Date.now();
        void qc.invalidateQueries({ queryKey: queryKeys.executions.detail(id), exact: true });
      };
      if (state.timer) return;
      const wait = Math.max(0, 500 - (now - state.last));
      state.timer = setTimeout(run, wait);
    };

    const connection = connectSse({
      url: buildUrl(`/api/v1/executions/${encodeURIComponent(id)}/events/stream`, { after: lastSequence }),
      lastEventId: lastSequence > 0 ? String(lastSequence) : null,
      getHeaders: async (): Promise<Record<string, string>> => {
        const token = await getAccessToken();
        return token ? { Authorization: `Bearer ${token}` } : {};
      },
      onOpen: () => setLiveState("live"),
      onError: () => setLiveState("reconnecting"),
      onFatal: (code) => {
        setLiveState("error");
        if (code === 401) notifyUnauthorized();
      },
      onMessage: (message) => {
        if (message.event === "transition") {
          try {
            const entry = JSON.parse(message.data) as HistoryEntry;
            qc.setQueryData<HistoryEntry[]>(queryKeys.executions.events(id), (old) => mergeEntries(old, [entry]));
            refreshExecution();
          } catch {
            // Ignore malformed payloads; the next refetch reconciles state.
          }
        } else if (message.event === "end") {
          connection.close();
          setLiveState("ended");
          void qc.invalidateQueries({ queryKey: queryKeys.executions.detail(id), exact: true });
        }
      },
    });
    return () => {
      connection.close();
      if (state.timer) clearTimeout(state.timer);
      state.timer = null;
    };
    // Reconnect only when the stream identity changes, not on every execution refetch.
  }, [id, shouldStream, qc]);

  const effectiveState: LiveState = alreadyComplete ? "ended" : shouldStream && liveState === "idle" ? "connecting" : liveState;
  return { execution, events, liveState: effectiveState };
}

export function isActive(execution: Execution | undefined): boolean {
  return Boolean(execution && !isTerminalExecution(execution.status));
}
