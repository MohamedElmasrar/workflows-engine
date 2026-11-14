import {
  applyEdgeChanges,
  applyNodeChanges,
  type Connection,
  type EdgeChange,
  type NodeChange,
} from "@xyflow/react";
import { create } from "zustand";
import { computeLayout } from "@/features/editor/auto-layout";
import { createsCycle, definitionToFlow, flowToDefinition, toFlowEdge } from "@/features/editor/flow-mapping";
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowNodeData } from "@/features/editor/flow-types";
import { defaultNodeName, NODE_CATALOG } from "@/features/editor/node-catalog";
import {
  isTriggerType,
  type NodeType,
  type Position,
  type ValidationIssue,
  type Workflow,
  type WorkflowDefinition,
  type WorkflowRole,
  type WorkflowSettings,
} from "@/lib/api/types";
import { defaultConfig } from "@/schemas/node-configs";

export interface WorkflowMeta {
  name: string;
  description: string;
  settings: WorkflowSettings | undefined;
}

export type ConnectResult = { ok: true; edgeId: string } | { ok: false; reason: string };

export interface EditorState {
  workflowId: string | null;
  revision: number;
  role: WorkflowRole;
  readOnly: boolean;
  meta: WorkflowMeta;
  nodes: WorkflowFlowNode[];
  edges: WorkflowFlowEdge[];
  selectedNodeId: string | null;
  selectedEdgeId: string | null;
  /** Monotonic counter of local edits; `dirty` = edits since the last save. */
  changeCounter: number;
  savedCounter: number;
  dirty: boolean;
  issues: ValidationIssue[];
  /** Incremented whenever the graph is replaced wholesale (load / restore); used to remount forms. */
  sessionKey: number;

  load: (workflow: Workflow) => void;
  /** Replaces the graph with another definition (e.g. restore a version) as unsaved changes. */
  loadDefinition: (definition: WorkflowDefinition) => void;
  markSaved: (revision: number, counterAtSave: number) => void;
  setRevision: (revision: number) => void;
  onNodesChange: (changes: NodeChange<WorkflowFlowNode>[]) => void;
  onEdgesChange: (changes: EdgeChange<WorkflowFlowEdge>[]) => void;
  canConnect: (connection: Connection) => ConnectResult;
  connect: (connection: Connection) => ConnectResult;
  addNode: (type: NodeType, position: Position) => string | null;
  replaceTrigger: (type: NodeType) => boolean;
  updateNode: (id: string, patch: Partial<WorkflowNodeData>) => void;
  deleteNode: (id: string) => void;
  deleteEdge: (id: string) => void;
  selectNode: (id: string | null) => void;
  selectEdge: (id: string | null) => void;
  setMeta: (patch: Partial<WorkflowMeta>) => void;
  setIssues: (issues: ValidationIssue[]) => void;
  applyLayout: (direction?: "LR" | "TB") => void;
  toDefinition: () => WorkflowDefinition;
  hasTrigger: () => boolean;
}

const EMPTY_META: WorkflowMeta = { name: "", description: "", settings: undefined };

/** Smallest `<prefix>_<n>` (n ≥ 1) not used yet. */
export function nextId(prefix: string, taken: Iterable<string>): string {
  const set = new Set(taken);
  let n = 1;
  while (set.has(`${prefix}_${n}`)) n++;
  return `${prefix}_${n}`;
}

function edited(state: EditorState): Pick<EditorState, "changeCounter" | "dirty"> {
  const changeCounter = state.changeCounter + 1;
  return { changeCounter, dirty: changeCounter !== state.savedCounter };
}

function applySelection(nodes: WorkflowFlowNode[], edges: WorkflowFlowEdge[], nodeId: string | null, edgeId: string | null) {
  return {
    nodes: nodes.map((n) => (Boolean(n.selected) === (n.id === nodeId) ? n : { ...n, selected: n.id === nodeId })),
    edges: edges.map((e) => (Boolean(e.selected) === (e.id === edgeId) ? e : { ...e, selected: e.id === edgeId })),
    selectedNodeId: nodeId,
    selectedEdgeId: edgeId,
  };
}

export const useEditorStore = create<EditorState>()((set, get) => ({
  workflowId: null,
  revision: 0,
  role: "VIEWER",
  readOnly: true,
  meta: EMPTY_META,
  nodes: [],
  edges: [],
  selectedNodeId: null,
  selectedEdgeId: null,
  changeCounter: 0,
  savedCounter: 0,
  dirty: false,
  issues: [],
  sessionKey: 0,

  load: (workflow) => {
    const graph = definitionToFlow(workflow.definition);
    set((state) => ({
      sessionKey: state.sessionKey + 1,
      workflowId: workflow.id,
      revision: workflow.revision,
      role: workflow.role,
      readOnly: workflow.role === "VIEWER" || workflow.status === "ARCHIVED",
      meta: { name: workflow.name, description: workflow.description ?? "", settings: graph.settings },
      nodes: graph.nodes,
      edges: graph.edges,
      selectedNodeId: null,
      selectedEdgeId: null,
      changeCounter: 0,
      savedCounter: 0,
      dirty: false,
      issues: [],
    }));
  },

  loadDefinition: (definition) => {
    const graph = definitionToFlow(definition);
    set((state) => ({
      nodes: graph.nodes,
      edges: graph.edges,
      meta: { ...state.meta, settings: graph.settings },
      selectedNodeId: null,
      selectedEdgeId: null,
      issues: [],
      sessionKey: state.sessionKey + 1,
      ...edited(state),
    }));
  },

  markSaved: (revision, counterAtSave) =>
    set((state) => ({ revision, savedCounter: counterAtSave, dirty: state.changeCounter !== counterAtSave })),

  setRevision: (revision) => set({ revision }),

  onNodesChange: (changes) => {
    const state = get();
    const relevant = state.readOnly
      ? changes.filter((c) => c.type === "select" || c.type === "dimensions")
      : changes;
    if (relevant.length === 0) return;
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
    set({ nodes, edges, selectedNodeId, selectedEdgeId, ...(isEdit ? edited(state) : {}) });
  },

  onEdgesChange: (changes) => {
    const state = get();
    const relevant = state.readOnly ? changes.filter((c) => c.type === "select") : changes;
    if (relevant.length === 0) return;
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
    set({ ...patch, ...(isEdit ? edited(state) : {}) });
  },

  canConnect: (connection) => {
    const { nodes, edges, readOnly } = get();
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
  },

  connect: (connection) => {
    const result = get().canConnect(connection);
    if (!result.ok) return result;
    const handle = connection.sourceHandle === "true" || connection.sourceHandle === "false" ? connection.sourceHandle : null;
    const edge = toFlowEdge({ id: result.edgeId, source: connection.source, target: connection.target, sourceHandle: handle });
    set((state) => ({ edges: [...state.edges, edge], ...edited(state) }));
    return result;
  },

  addNode: (type, position) => {
    const state = get();
    if (state.readOnly) return null;
    if (isTriggerType(type) && state.hasTrigger()) return null;
    const entry = NODE_CATALOG[type];
    const id = nextId(entry.idPrefix, state.nodes.map((n) => n.id));
    const node: WorkflowFlowNode = {
      id,
      type: entry.kind,
      position: { x: Math.round(position.x), y: Math.round(position.y) },
      data: { nodeType: type, name: defaultNodeName(type), config: defaultConfig(type) },
      selected: true,
    };
    const selection = applySelection([...state.nodes, node], state.edges, id, null);
    set({ ...selection, ...edited(state) });
    return id;
  },

  replaceTrigger: (type) => {
    const state = get();
    if (state.readOnly || !isTriggerType(type)) return false;
    const trigger = state.nodes.find((n) => isTriggerType(n.data.nodeType));
    if (!trigger) return state.addNode(type, { x: 0, y: 0 }) !== null;
    if (trigger.data.nodeType === type) return false;
    set({
      nodes: state.nodes.map((n) =>
        n.id === trigger.id
          ? {
              ...n,
              type: NODE_CATALOG[type].kind,
              data: { nodeType: type, name: defaultNodeName(type), config: defaultConfig(type) },
            }
          : n,
      ),
      ...edited(state),
    });
    return true;
  },

  updateNode: (id, patch) => {
    const state = get();
    if (state.readOnly) return;
    let changed = false;
    const nodes = state.nodes.map((n) => {
      if (n.id !== id) return n;
      changed = true;
      const data: WorkflowNodeData = { ...n.data, ...patch };
      // Explicit `undefined` in the patch removes optional fields.
      if ("timeoutSeconds" in patch && patch.timeoutSeconds === undefined) delete data.timeoutSeconds;
      if ("retryPolicy" in patch && patch.retryPolicy === undefined) delete data.retryPolicy;
      return { ...n, data };
    });
    if (changed) set({ nodes, ...edited(state) });
  },

  deleteNode: (id) => {
    const state = get();
    if (state.readOnly || !state.nodes.some((n) => n.id === id)) return;
    set({
      nodes: state.nodes.filter((n) => n.id !== id),
      edges: state.edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeId: state.selectedNodeId === id ? null : state.selectedNodeId,
      ...edited(state),
    });
  },

  deleteEdge: (id) => {
    const state = get();
    if (state.readOnly || !state.edges.some((e) => e.id === id)) return;
    set({
      edges: state.edges.filter((e) => e.id !== id),
      selectedEdgeId: state.selectedEdgeId === id ? null : state.selectedEdgeId,
      ...edited(state),
    });
  },

  selectNode: (id) => set((state) => applySelection(state.nodes, state.edges, id, null)),
  selectEdge: (id) => set((state) => applySelection(state.nodes, state.edges, null, id)),

  setMeta: (patch) => {
    const state = get();
    if (state.readOnly) return;
    set({ meta: { ...state.meta, ...patch }, ...edited(state) });
  },

  setIssues: (issues) => set({ issues }),

  applyLayout: (direction = "LR") => {
    const state = get();
    if (state.readOnly || state.nodes.length === 0) return;
    const positions = computeLayout(
      state.nodes.map((n) => ({ id: n.id, width: n.measured?.width, height: n.measured?.height })),
      state.edges,
      direction,
    );
    set({
      nodes: state.nodes.map((n) => ({ ...n, position: positions.get(n.id) ?? n.position })),
      ...edited(state),
    });
  },

  toDefinition: () => {
    const { nodes, edges, meta } = get();
    return flowToDefinition(nodes, edges, meta.settings);
  },

  hasTrigger: () => get().nodes.some((n) => isTriggerType(n.data.nodeType)),
}));

/** Resets the store (used by tests and when leaving the editor). */
export function resetEditorStore(): void {
  useEditorStore.setState({
    workflowId: null,
    revision: 0,
    role: "VIEWER",
    readOnly: true,
    meta: EMPTY_META,
    nodes: [],
    edges: [],
    selectedNodeId: null,
    selectedEdgeId: null,
    changeCounter: 0,
    savedCounter: 0,
    dirty: false,
    issues: [],
  });
}
