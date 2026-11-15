/** Small pure helpers used by the editor store: initial state, ids, dirty tracking, selection. */
import type { WorkflowFlowEdge, WorkflowFlowNode } from "@/features/editor/flow-types";
import type { EditorState, WorkflowMeta } from "./types";

export const EMPTY_META: WorkflowMeta = { name: "", description: "", settings: undefined };

/** Data part of a fresh, empty editor (no workflow loaded). */
export const INITIAL_DATA = {
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
} satisfies Partial<EditorState>;

/** Smallest `<prefix>_<n>` (n ≥ 1) not used yet. */
export function nextId(prefix: string, taken: Iterable<string>): string {
  const set = new Set(taken);
  let n = 1;
  while (set.has(`${prefix}_${n}`)) n++;
  return `${prefix}_${n}`;
}

export function edited(state: EditorState): Pick<EditorState, "changeCounter" | "dirty"> {
  const changeCounter = state.changeCounter + 1;
  return { changeCounter, dirty: changeCounter !== state.savedCounter };
}

export function applySelection(nodes: WorkflowFlowNode[], edges: WorkflowFlowEdge[], nodeId: string | null, edgeId: string | null) {
  return {
    nodes: nodes.map((n) => (Boolean(n.selected) === (n.id === nodeId) ? n : { ...n, selected: n.id === nodeId })),
    edges: edges.map((e) => (Boolean(e.selected) === (e.id === edgeId) ? e : { ...e, selected: e.id === edgeId })),
    selectedNodeId: nodeId,
    selectedEdgeId: edgeId,
  };
}
