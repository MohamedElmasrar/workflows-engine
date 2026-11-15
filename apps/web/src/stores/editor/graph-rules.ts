/**
 * Pure graph editing rules (no store access): which connections are allowed, how React Flow change events
 * update nodes/edges/selection, and what a newly added node looks like.
 */
import { applyEdgeChanges, applyNodeChanges, type Connection, type EdgeChange, type NodeChange } from "@xyflow/react";
import { createsCycle } from "@/features/editor/flow-mapping";
import type { WorkflowFlowEdge, WorkflowFlowNode } from "@/features/editor/flow-types";
import { defaultNodeName, NODE_CATALOG } from "@/features/editor/node-catalog";
import { isTriggerType, type NodeType, type Position } from "@/lib/api/types";
import { defaultConfig } from "@/schemas/node-configs";
import { nextId } from "./state-helpers";
import type { ConnectResult, EditorState } from "./types";

type GraphState = Pick<EditorState, "nodes" | "edges" | "readOnly" | "selectedNodeId" | "selectedEdgeId">;

/** Checks a connection against the DAG rules; returns the id of the new edge when allowed. */
export function checkConnection(state: Pick<EditorState, "nodes" | "edges" | "readOnly">, connection: Connection): ConnectResult {
  const { nodes, edges, readOnly } = state;
  if (readOnly) return { ok: false, reason: "The workflow is read-only" };
  const { source, target } = connection;
  if (!source || !target) return { ok: false, reason: "Incomplete connection" };
  if (source === target) return { ok: false, reason: "A node cannot connect to itself" };
  const sourceNode = nodes.find((n) => n.id === source);
  const targetNode = nodes.find((n) => n.id === target);
  if (!sourceNode || !targetNode) return { ok: false, reason: "Unknown node" };
  if (isTriggerType(targetNode.data.nodeType)) return { ok: false, reason: "Triggers cannot have incoming connections" };
  const handle = connection.sourceHandle ?? null;
  if (sourceNode.data.nodeType === "CONDITION") {
    if (handle !== "true" && handle !== "false") return { ok: false, reason: "Use the true or false handle of the condition" };
  } else if (handle) {
    return { ok: false, reason: "Only conditions have branch handles" };
  }
  if (edges.some((e) => e.source === source && e.target === target)) {
    return { ok: false, reason: "These nodes are already connected" };
  }
  if (createsCycle(source, target, edges)) return { ok: false, reason: "Connections must not create a cycle" };
  return { ok: true, edgeId: nextId("e", edges.map((e) => e.id)) };
}

/**
 * Applies React Flow node changes. Read-only editors only accept selection and measurement changes. Returns the
 * state patch and whether the draft was edited, or null when nothing applies.
 */
export function applyNodeChangeEvents(state: GraphState, changes: NodeChange<WorkflowFlowNode>[]) {
  const relevant = state.readOnly ? changes.filter((c) => c.type === "select" || c.type === "dimensions") : changes;
  if (relevant.length === 0) return null;
  const removed = new Set(relevant.filter((c) => c.type === "remove").map((c) => c.id));
  const isEdit = relevant.some(
    (c) => c.type === "remove" || c.type === "add" || c.type === "replace" || (c.type === "position" && c.position !== undefined),
  );
  const nodes = applyNodeChanges(relevant, state.nodes);
  let edges = removed.size ? state.edges.filter((e) => !removed.has(e.source) && !removed.has(e.target)) : state.edges;
  const selected = nodes.find((n) => n.selected)?.id ?? null;
  const selectChange = relevant.some((c) => c.type === "select");
  let selectedNodeId = selectChange ? selected : state.selectedNodeId;
  if (selectedNodeId && removed.has(selectedNodeId)) selectedNodeId = null;
  let selectedEdgeId = state.selectedEdgeId;
  if (selectChange && selected) {
    selectedEdgeId = null;
    edges = edges.map((e) => (e.selected ? { ...e, selected: false } : e));
  }
  if (selectedEdgeId && !edges.some((e) => e.id === selectedEdgeId)) selectedEdgeId = null;
  return { patch: { nodes, edges, selectedNodeId, selectedEdgeId }, isEdit };
}

/** Same as {@link applyNodeChangeEvents} for edge changes. */
export function applyEdgeChangeEvents(state: GraphState, changes: EdgeChange<WorkflowFlowEdge>[]) {
  const relevant = state.readOnly ? changes.filter((c) => c.type === "select") : changes;
  if (relevant.length === 0) return null;
  const isEdit = relevant.some((c) => c.type === "remove" || c.type === "add" || c.type === "replace");
  const edges = applyEdgeChanges(relevant, state.edges);
  const selectChange = relevant.some((c) => c.type === "select");
  const selected = edges.find((e) => e.selected)?.id ?? null;
  let selectedEdgeId = selectChange ? selected : state.selectedEdgeId;
  if (selectedEdgeId && !edges.some((e) => e.id === selectedEdgeId)) selectedEdgeId = null;
  const patch: Partial<EditorState> = { edges, selectedEdgeId };
  if (selectChange && selected) {
    patch.selectedNodeId = null;
    patch.nodes = state.nodes.map((n) => (n.selected ? { ...n, selected: false } : n));
  }
  return { patch, isEdit };
}

/** A new node of the given type with default name and config, selected, at a rounded position. */
export function createNode(type: NodeType, position: Position, existing: WorkflowFlowNode[]): WorkflowFlowNode {
  const entry = NODE_CATALOG[type];
  return {
    id: nextId(entry.idPrefix, existing.map((n) => n.id)),
    type: entry.kind,
    position: { x: Math.round(position.x), y: Math.round(position.y) },
    data: { nodeType: type, name: defaultNodeName(type), config: defaultConfig(type) },
    selected: true,
  };
}

/** The trigger node turned into another trigger type (fresh name and config). */
export function convertTrigger(node: WorkflowFlowNode, type: NodeType): WorkflowFlowNode {
  return { ...node, type: NODE_CATALOG[type].kind, data: { nodeType: type, name: defaultNodeName(type), config: defaultConfig(type) } };
}
