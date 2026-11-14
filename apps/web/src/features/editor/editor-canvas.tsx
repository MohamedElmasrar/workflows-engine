"use client";

import {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  ReactFlow,
  useReactFlow,
  type Connection,
  type Edge,
  type IsValidConnection,
} from "@xyflow/react";
import { useCallback, useMemo, type DragEvent } from "react";
import { toast } from "sonner";
import { useShallow } from "zustand/react/shallow";
import { NODE_TYPES, type NodeType } from "@/lib/api/types";
import { usePreferences } from "@/lib/preferences";
import { useEditorStore } from "@/stores/editor-store";
import { validateNode } from "@/schemas/node-configs";
import { GraphDecorationsContext, nodeTypes, type GraphDecorations } from "./nodes/workflow-nodes";
import type { WorkflowFlowNode } from "./flow-types";

export const DND_MIME = "application/x-workflow-node-type";

/** Maps server validation issues plus local schema checks to per-node messages. */
function useIssuesById(): Record<string, string[]> {
  const issues = useEditorStore((s) => s.issues);
  const nodes = useEditorStore((s) => s.nodes);
  return useMemo(() => {
    const out: Record<string, string[]> = {};
    for (const issue of issues) {
      if (issue.nodeId) (out[issue.nodeId] ??= []).push(`${issue.code}: ${issue.message}`);
    }
    for (const node of nodes) {
      const local = validateNode({ type: node.data.nodeType, ...node.data });
      if (local.length) (out[node.id] ??= []).push(...local.map((i) => `${i.path}: ${i.message}`));
    }
    return out;
  }, [issues, nodes]);
}

export function EditorCanvas({ statusById }: { statusById?: GraphDecorations["statusById"] }) {
  const { nodes, edges, readOnly, issues } = useEditorStore(
    useShallow((s) => ({ nodes: s.nodes, edges: s.edges, readOnly: s.readOnly, issues: s.issues })),
  );
  const onNodesChange = useEditorStore((s) => s.onNodesChange);
  const onEdgesChange = useEditorStore((s) => s.onEdgesChange);
  const connect = useEditorStore((s) => s.connect);
  const canConnect = useEditorStore((s) => s.canConnect);
  const addNode = useEditorStore((s) => s.addNode);
  const hasTrigger = useEditorStore((s) => s.hasTrigger);
  const [prefs] = usePreferences();
  const { screenToFlowPosition } = useReactFlow();
  const issuesById = useIssuesById();

  const invalidEdgeIds = useMemo(() => new Set(issues.map((i) => i.edgeId).filter(Boolean)), [issues]);
  const decoratedEdges = useMemo<Edge[]>(
    () => edges.map((e) => (invalidEdgeIds.has(e.id) ? { ...e, className: "edge-invalid" } : e)),
    [edges, invalidEdgeIds],
  );
  const decorations = useMemo<GraphDecorations>(
    () => ({ issuesById, statusById, layoutDirection: prefs.layoutDirection }),
    [issuesById, statusById, prefs.layoutDirection],
  );

  const onConnect = useCallback(
    (connection: Connection) => {
      const result = connect(connection);
      if (!result.ok) toast.error(result.reason);
    },
    [connect],
  );

  const isValidConnection = useCallback<IsValidConnection>(
    (connection) =>
      canConnect({
        source: connection.source,
        target: connection.target,
        sourceHandle: connection.sourceHandle ?? null,
        targetHandle: connection.targetHandle ?? null,
      }).ok,
    [canConnect],
  );

  const onDragOver = useCallback((event: DragEvent) => {
    if (event.dataTransfer.types.includes(DND_MIME)) {
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
    }
  }, []);

  const onDrop = useCallback(
    (event: DragEvent) => {
      const type = event.dataTransfer.getData(DND_MIME);
      if (!type || !(NODE_TYPES as readonly string[]).includes(type)) return;
      event.preventDefault();
      const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const id = addNode(type as NodeType, { x: position.x - 120, y: position.y - 38 });
      if (!id && hasTrigger()) toast.error("A workflow can only have one trigger. Use “Replace” in the palette instead.");
    },
    [addNode, hasTrigger, screenToFlowPosition],
  );

  return (
    <GraphDecorationsContext.Provider value={decorations}>
      <div className="h-full w-full" onDragOver={onDragOver} onDrop={onDrop} data-testid="editor-canvas">
        <ReactFlow<WorkflowFlowNode, Edge>
          nodes={nodes}
          edges={decoratedEdges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          isValidConnection={isValidConnection}
          nodesDraggable={!readOnly}
          nodesConnectable={!readOnly}
          edgesReconnectable={false}
          elementsSelectable
          deleteKeyCode={readOnly ? null : ["Delete", "Backspace"]}
          fitView
          fitViewOptions={{ padding: 0.12, maxZoom: 1.2 }}
          minZoom={0.2}
          maxZoom={2}
          proOptions={{ hideAttribution: true }}
          aria-label="Workflow graph"
        >
          <Background variant={BackgroundVariant.Dots} gap={18} size={1} />
          <Controls showInteractive={false} />
          {prefs.showMinimap ? <MiniMap pannable zoomable ariaLabel="Minimap" nodeColor="#94a3b8" nodeStrokeWidth={3} /> : null}
        </ReactFlow>
      </div>
    </GraphDecorationsContext.Provider>
  );
}
