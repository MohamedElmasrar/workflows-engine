import type { Edge, Node } from "@xyflow/react";
import type { NodeType, RetryPolicy } from "@/lib/api/types";
import type { FlowNodeKind } from "./node-catalog";

/** Data carried by every React Flow node; mirrors the editable fields of a definition node. */
export type WorkflowNodeData = {
  nodeType: NodeType;
  name: string;
  config: Record<string, unknown>;
  timeoutSeconds?: number;
  retryPolicy?: RetryPolicy;
};

export type WorkflowFlowNode = Node<WorkflowNodeData, FlowNodeKind>;
export type WorkflowFlowEdge = Edge;
