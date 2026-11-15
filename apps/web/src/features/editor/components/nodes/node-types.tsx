"use client";
/** Maps React Flow node kinds (trigger, action, condition, delay) to their memoised components. */
import type { NodeProps, NodeTypes } from "@xyflow/react";
import { memo } from "react";
import type { WorkflowFlowNode } from "../../lib/flow-types";
import { NodeShell } from "./node-shell";

const TriggerNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="trigger" />);
TriggerNode.displayName = "TriggerNode";
const ActionNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="action" />);
ActionNode.displayName = "ActionNode";
const ConditionNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="condition" />);
ConditionNode.displayName = "ConditionNode";
const DelayNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="delay" />);
DelayNode.displayName = "DelayNode";

export const nodeTypes: NodeTypes = {
  trigger: TriggerNode,
  action: ActionNode,
  condition: ConditionNode,
  delay: DelayNode,
};
