/** Converts between stored workflow definitions and React Flow nodes/edges. */
import type {
  BranchHandle,
  DefinitionEdge,
  DefinitionNode,
  WorkflowDefinition,
  WorkflowSettings,
} from "@/lib/api/types";
import { computeLayout } from "./auto-layout";
import type { WorkflowFlowEdge, WorkflowFlowNode } from "./flow-types";
import { NODE_CATALOG } from "./node-catalog";

export interface FlowGraph {
  nodes: WorkflowFlowNode[];
  edges: WorkflowFlowEdge[];
  settings: WorkflowSettings | undefined;
}

export function isBranchHandle(value: unknown): value is BranchHandle {
  return value === "true" || value === "false";
}

/** React Flow edge for a definition edge (branch edges get a visible label). */
export function toFlowEdge(edge: DefinitionEdge): WorkflowFlowEdge {
  const handle = isBranchHandle(edge.sourceHandle) ? edge.sourceHandle : null;
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    sourceHandle: handle,
    type: "smoothstep",
    ...(handle
      ? {
          label: handle,
          labelBgPadding: [6, 2] as [number, number],
          labelBgBorderRadius: 4,
          labelStyle: { fontSize: 11, fontWeight: 600, fill: handle === "true" ? "#047857" : "#b91c1c" },
        }
      : {}),
  };
}

/**
 * Converts a stored definition into React Flow nodes/edges. Nodes without a position are laid
 * out automatically. Pure; no React Flow runtime state (selection, measurements) is created.
 */
export function definitionToFlow(definition: WorkflowDefinition): FlowGraph {
  const missing = definition.nodes.filter((n) => !n.position);
  const layout = missing.length > 0 ? computeLayout(definition.nodes, definition.edges) : null;
  const nodes: WorkflowFlowNode[] = definition.nodes.map((node) => ({
    id: node.id,
    type: NODE_CATALOG[node.type]?.kind ?? "action",
    position: node.position ? { x: node.position.x, y: node.position.y } : (layout?.get(node.id) ?? { x: 0, y: 0 }),
    data: {
      nodeType: node.type,
      name: node.name,
      config: structuredClone(node.config ?? {}),
      ...(node.timeoutSeconds !== undefined ? { timeoutSeconds: node.timeoutSeconds } : {}),
      ...(node.retryPolicy !== undefined ? { retryPolicy: { ...node.retryPolicy } } : {}),
    },
  }));
  const edges = definition.edges.map(toFlowEdge);
  return { nodes, edges, settings: definition.settings ? { ...definition.settings } : undefined };
}

/** Converts editor state back into a schema-conformant definition (no extra fields). */
export function flowToDefinition(
  nodes: readonly WorkflowFlowNode[],
  edges: readonly WorkflowFlowEdge[],
  settings?: WorkflowSettings,
): WorkflowDefinition {
  const definitionNodes: DefinitionNode[] = nodes.map((node) => {
    const out: DefinitionNode = {
      id: node.id,
      type: node.data.nodeType,
      name: node.data.name,
      position: { x: Math.round(node.position.x), y: Math.round(node.position.y) },
      config: structuredClone(node.data.config),
    };
    if (node.data.timeoutSeconds !== undefined) out.timeoutSeconds = node.data.timeoutSeconds;
    if (node.data.retryPolicy !== undefined) out.retryPolicy = { ...node.data.retryPolicy };
    return out;
  });
  const definitionEdges: DefinitionEdge[] = edges.map((edge) => {
    const out: DefinitionEdge = { id: edge.id, source: edge.source, target: edge.target };
    if (isBranchHandle(edge.sourceHandle)) out.sourceHandle = edge.sourceHandle;
    return out;
  });
  const definition: WorkflowDefinition = { schemaVersion: 1, nodes: definitionNodes, edges: definitionEdges };
  const cleanSettings = cleanWorkflowSettings(settings);
  if (cleanSettings) definition.settings = cleanSettings;
  return definition;
}

function cleanWorkflowSettings(settings: WorkflowSettings | undefined): WorkflowSettings | undefined {
  if (!settings) return undefined;
  const out: WorkflowSettings = {};
  if (settings.executionTimeoutSeconds !== undefined) out.executionTimeoutSeconds = settings.executionTimeoutSeconds;
  if (settings.maxConcurrentExecutions !== undefined) out.maxConcurrentExecutions = settings.maxConcurrentExecutions;
  return out;
}

/** Ids of all nodes from which `nodeId` is reachable (its ancestors), nearest first. */
export function ancestorsOf(nodeId: string, edges: readonly { source: string; target: string }[]): string[] {
  const incoming = new Map<string, string[]>();
  for (const e of edges) incoming.set(e.target, [...(incoming.get(e.target) ?? []), e.source]);
  const seen = new Set<string>();
  const order: string[] = [];
  const queue = [...(incoming.get(nodeId) ?? [])];
  while (queue.length) {
    const id = queue.shift() as string;
    if (seen.has(id) || id === nodeId) continue;
    seen.add(id);
    order.push(id);
    queue.push(...(incoming.get(id) ?? []));
  }
  return order;
}

/** True when adding source→target would create a cycle. */
export function createsCycle(source: string, target: string, edges: readonly { source: string; target: string }[]): boolean {
  if (source === target) return true;
  return ancestorsOf(source, edges).includes(target);
}
