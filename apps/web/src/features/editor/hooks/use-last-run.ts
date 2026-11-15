/** "Show last run" overlay: loads the latest execution of the workflow and maps node ids to their status. */
import { useMemo, useState } from "react";
import type { NodeStatus } from "@/lib/api/types";
import { useLatestExecution } from "@/features/executions/hooks/execution-queries";

export function useLastRun(workflowId: string) {
  const [enabled, setEnabled] = useState(false);
  const { latest, detail } = useLatestExecution(workflowId, enabled);

  const statusById = useMemo(() => {
    if (!enabled || !detail.data) return undefined;
    const out: Record<string, NodeStatus> = {};
    for (const n of detail.data.nodes) out[n.nodeId] = n.status;
    return out;
  }, [enabled, detail.data]);

  return {
    enabled,
    setEnabled,
    /** undefined = overlay off, null = no run yet, otherwise the execution */
    execution: enabled ? (latest.data === null ? null : detail.data) : undefined,
    statusById,
  };
}
