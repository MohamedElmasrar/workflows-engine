/** Shape of the editor store: the editable draft (graph + workflow meta), selection, dirty tracking. */
import type { Connection, EdgeChange, NodeChange } from "@xyflow/react";
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowNodeData } from "@/features/editor/lib/flow-types";
import type {
  NodeType,
  Position,
  ValidationIssue,
  Workflow,
  WorkflowDefinition,
  WorkflowRole,
  WorkflowSettings,
} from "@/lib/api/types";

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
