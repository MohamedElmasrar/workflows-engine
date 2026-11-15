/** Context through which the editor or the execution view decorates nodes (status, validation issues, layout direction). */
import { createContext, useContext } from "react";
import type { NodeStatus } from "@/lib/api/types";

/** Per-graph decorations supplied by the editor or the execution view. */
export interface GraphDecorations {
  statusById?: Record<string, NodeStatus>;
  issuesById?: Record<string, string[]>;
  layoutDirection?: "LR" | "TB";
}

export const GraphDecorationsContext = createContext<GraphDecorations>({});

export function useNodeDecorations(id: string) {
  const ctx = useContext(GraphDecorationsContext);
  return {
    status: ctx.statusById?.[id],
    issues: ctx.issuesById?.[id],
    vertical: ctx.layoutDirection === "TB",
  };
}
