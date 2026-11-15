/** Builds the list of `{{ ... }}` references a node may use: the trigger, its upstream nodes and execution metadata. */
import { isTriggerType, type NodeType } from "@/lib/api/types";
import { ancestorsOf } from "./flow-mapping";

export interface TemplateReference {
  value: string;
  label: string;
}

const OUTPUT_PATHS: Partial<Record<NodeType, string[]>> = {
  HTTP_REQUEST: ["body", "status", "headers"],
  CONDITION: ["result"],
  LOG: ["message"],
  NOTIFICATION: ["deliveredAt"],
  DELAY: ["resumedAt"],
};

const TRIGGER_PATHS: Record<string, { path: string; label: string }[]> = {
  MANUAL_TRIGGER: [{ path: "trigger", label: "Manual run input (whole object)" }],
  WEBHOOK_TRIGGER: [
    { path: "trigger.body", label: "Webhook JSON body" },
    { path: "trigger.headers", label: "Webhook request headers" },
    { path: "trigger.query", label: "Webhook query parameters" },
  ],
  SCHEDULE_TRIGGER: [
    { path: "trigger.payload", label: "Schedule payload" },
    { path: "trigger.scheduledAt", label: "Scheduled fire time" },
  ],
};

/** Builds the list of references usable by `nodeId`: trigger output, upstream outputs, execution fields. */
export function buildReferences(
  nodeId: string,
  nodes: { id: string; name: string; type: NodeType }[],
  edges: { source: string; target: string }[],
): TemplateReference[] {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const refs: TemplateReference[] = [];
  const trigger = nodes.find((n) => isTriggerType(n.type));
  if (trigger && trigger.id !== nodeId) {
    for (const item of TRIGGER_PATHS[trigger.type] ?? []) refs.push({ value: `{{ ${item.path} }}`, label: item.label });
  }
  for (const id of ancestorsOf(nodeId, edges)) {
    const node = byId.get(id);
    if (!node || isTriggerType(node.type)) continue;
    refs.push({ value: `{{ nodes.${id}.output }}`, label: `Output of “${node.name}”` });
    for (const path of OUTPUT_PATHS[node.type] ?? []) {
      refs.push({ value: `{{ nodes.${id}.output.${path} }}`, label: `“${node.name}” → ${path}` });
    }
  }
  refs.push(
    { value: "{{ execution.id }}", label: "Execution id" },
    { value: "{{ execution.workflowId }}", label: "Workflow id" },
    { value: "{{ execution.workflowVersion }}", label: "Workflow version" },
  );
  return refs;
}
