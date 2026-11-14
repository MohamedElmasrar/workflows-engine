"use client";

import { Background, BackgroundVariant, Controls, MiniMap, ReactFlow, ReactFlowProvider, type Edge } from "@xyflow/react";
import { useMemo } from "react";
import type { NodeExecution, NodeStatus, WorkflowDefinition } from "@/lib/api/types";
import { usePreferences } from "@/lib/preferences";
import { definitionToFlow } from "@/features/editor/flow-mapping";
import type { WorkflowFlowNode } from "@/features/editor/flow-types";
import { GraphDecorationsContext, nodeTypes, type GraphDecorations } from "@/features/editor/nodes/workflow-nodes";

/** Classifies edges: active (taken), inactive (not taken), or pending. */
export function edgeState(
  edge: { source: string; sourceHandle?: string | null },
  nodesById: Map<string, Pick<NodeExecution, "status" | "branch">>,
): "active" | "inactive" | "pending" {
  const source = nodesById.get(edge.source);
  if (!source) return "pending";
  if (source.status === "COMPLETED") {
    if (!edge.sourceHandle) return "active";
    return source.branch === edge.sourceHandle ? "active" : "inactive";
  }
  if (["FAILED", "SKIPPED", "CANCELLED", "TIMED_OUT"].includes(source.status)) return "inactive";
  return "pending";
}

/**
 * Read-only graph of a definition, optionally coloured by node execution status.
 * Has its own ReactFlowProvider so it never shares state with the editor canvas.
 */
export function ExecutionGraph({
  definition,
  nodeExecutions,
  selectedNodeId,
  onSelectNode,
  className,
}: {
  definition: WorkflowDefinition;
  nodeExecutions?: NodeExecution[];
  selectedNodeId?: string | null;
  onSelectNode?: (nodeId: string | null) => void;
  className?: string;
}) {
  const [prefs] = usePreferences();
  const graph = useMemo(() => definitionToFlow(definition), [definition]);
  const byId = useMemo(() => new Map((nodeExecutions ?? []).map((n) => [n.nodeId, n])), [nodeExecutions]);

  const nodes = useMemo<WorkflowFlowNode[]>(
    () => graph.nodes.map((n) => ({ ...n, selected: n.id === selectedNodeId, draggable: false, connectable: false })),
    [graph.nodes, selectedNodeId],
  );
  const edges = useMemo<Edge[]>(
    () =>
      graph.edges.map((e) => {
        if (!nodeExecutions) return e;
        const state = edgeState(e, byId);
        return { ...e, className: state === "active" ? "edge-active" : state === "inactive" ? "edge-inactive" : undefined, animated: state === "pending" && byId.get(e.source)?.status === "RUNNING" };
      }),
    [graph.edges, nodeExecutions, byId],
  );
  const decorations = useMemo<GraphDecorations>(() => {
    if (!nodeExecutions) return { layoutDirection: prefs.layoutDirection };
    const statusById: Record<string, NodeStatus> = {};
    for (const n of nodeExecutions) statusById[n.nodeId] = n.status;
    return { statusById, layoutDirection: prefs.layoutDirection };
  }, [nodeExecutions, prefs.layoutDirection]);

  return (
    <ReactFlowProvider>
      <GraphDecorationsContext.Provider value={decorations}>
        <div className={className ?? "h-full w-full"} data-testid="execution-graph">
          <ReactFlow<WorkflowFlowNode, Edge>
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            nodesDraggable={false}
            nodesConnectable={false}
            edgesFocusable={false}
            deleteKeyCode={null}
            onNodeClick={(_e, node) => onSelectNode?.(node.id)}
            onPaneClick={() => onSelectNode?.(null)}
            fitView
            fitViewOptions={{ padding: 0.12, maxZoom: 1.1 }}
            minZoom={0.2}
            proOptions={{ hideAttribution: true }}
            aria-label="Execution graph"
          >
            <Background variant={BackgroundVariant.Dots} gap={18} size={1} />
            <Controls showInteractive={false} />
            {prefs.showMinimap ? <MiniMap pannable zoomable ariaLabel="Minimap" nodeColor="#94a3b8" nodeStrokeWidth={3} /> : null}
          </ReactFlow>
        </div>
      </GraphDecorationsContext.Provider>
    </ReactFlowProvider>
  );
}
