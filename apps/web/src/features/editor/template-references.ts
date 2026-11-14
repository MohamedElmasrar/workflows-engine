"use client";

import { useMemo } from "react";
import { useShallow } from "zustand/react/shallow";
import { isTriggerType, type NodeType } from "@/lib/api/types";
import { useEditorStore } from "@/stores/editor-store";
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

export function useTemplateReferences(nodeId: string): TemplateReference[] {
  const nodes = useEditorStore(
    useShallow((s) => s.nodes.map((n) => `${n.id}\u0000${n.data.name}\u0000${n.data.nodeType}`)),
  );
  const edges = useEditorStore(useShallow((s) => s.edges.map((e) => `${e.source}\u0000${e.target}`)));
  return useMemo(() => {
    const parsedNodes = nodes.map((key) => {
      const [id, name, type] = key.split("\u0000");
      return { id, name, type: type as NodeType };
    });
    const parsedEdges = edges.map((key) => {
      const [source, target] = key.split("\u0000");
      return { source, target };
    });
    return buildReferences(nodeId, parsedNodes, parsedEdges);
  }, [nodeId, nodes, edges]);
}
