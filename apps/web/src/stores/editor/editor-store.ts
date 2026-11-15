/**
 * Zustand store holding the editable workflow draft. Server data stays in TanStack Query; this store only owns
 * what the user is editing. Graph rules live in ./graph-rules, helpers in ./state-helpers.
 */
import { create } from "zustand";
import { computeLayout } from "@/features/editor/auto-layout";
import { definitionToFlow, flowToDefinition, toFlowEdge } from "@/features/editor/flow-mapping";
import type { WorkflowNodeData } from "@/features/editor/flow-types";
import { isTriggerType } from "@/lib/api/types";
import { applyEdgeChangeEvents, applyNodeChangeEvents, checkConnection, convertTrigger, createNode } from "./graph-rules";
import { applySelection, edited, INITIAL_DATA } from "./state-helpers";
import type { EditorState } from "./types";

export const useEditorStore = create<EditorState>()((set, get) => ({
  ...INITIAL_DATA,
  sessionKey: 0,

  load: (workflow) => {
    const graph = definitionToFlow(workflow.definition);
    set((state) => ({
      ...INITIAL_DATA,
      sessionKey: state.sessionKey + 1,
      workflowId: workflow.id,
      revision: workflow.revision,
      role: workflow.role,
      readOnly: workflow.role === "VIEWER" || workflow.status === "ARCHIVED",
      meta: { name: workflow.name, description: workflow.description ?? "", settings: graph.settings },
      nodes: graph.nodes,
      edges: graph.edges,
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
    const result = applyNodeChangeEvents(state, changes);
    if (result) set({ ...result.patch, ...(result.isEdit ? edited(state) : {}) });
  },

  onEdgesChange: (changes) => {
    const state = get();
    const result = applyEdgeChangeEvents(state, changes);
    if (result) set({ ...result.patch, ...(result.isEdit ? edited(state) : {}) });
  },

  canConnect: (connection) => checkConnection(get(), connection),

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
    const node = createNode(type, position, state.nodes);
    set({ ...applySelection([...state.nodes, node], state.edges, node.id, null), ...edited(state) });
    return node.id;
  },

  replaceTrigger: (type) => {
    const state = get();
    if (state.readOnly || !isTriggerType(type)) return false;
    const trigger = state.nodes.find((n) => isTriggerType(n.data.nodeType));
    if (!trigger) return state.addNode(type, { x: 0, y: 0 }) !== null;
    if (trigger.data.nodeType === type) return false;
    set({ nodes: state.nodes.map((n) => (n.id === trigger.id ? convertTrigger(n, type) : n)), ...edited(state) });
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
    set({ nodes: state.nodes.map((n) => ({ ...n, position: positions.get(n.id) ?? n.position })), ...edited(state) });
  },

  toDefinition: () => {
    const { nodes, edges, meta } = get();
    return flowToDefinition(nodes, edges, meta.settings);
  },

  hasTrigger: () => get().nodes.some((n) => isTriggerType(n.data.nodeType)),
}));

/** Resets the store (used by tests and when leaving the editor). */
export function resetEditorStore(): void {
  useEditorStore.setState(INITIAL_DATA);
}
