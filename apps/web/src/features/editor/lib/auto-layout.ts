/** Dagre-based automatic layout for workflow graphs. */
import dagre from "@dagrejs/dagre";
import type { Position } from "@/lib/api/types";

export const DEFAULT_NODE_WIDTH = 240;
export const DEFAULT_NODE_HEIGHT = 76;

export interface LayoutNode {
  id: string;
  width?: number;
  height?: number;
}

/**
 * Computes top-left positions with dagre (left-to-right by default). Returns a map so that
 * callers decide which nodes to move.
 */
export function computeLayout(
  nodes: LayoutNode[],
  edges: { source: string; target: string }[],
  direction: "LR" | "TB" = "LR",
): Map<string, Position> {
  const graph = new dagre.graphlib.Graph();
  graph.setGraph({ rankdir: direction, nodesep: 50, ranksep: 90, marginx: 20, marginy: 20 });
  graph.setDefaultEdgeLabel(() => ({}));
  for (const node of nodes) {
    graph.setNode(node.id, { width: node.width ?? DEFAULT_NODE_WIDTH, height: node.height ?? DEFAULT_NODE_HEIGHT });
  }
  for (const edge of edges) {
    if (graph.hasNode(edge.source) && graph.hasNode(edge.target)) graph.setEdge(edge.source, edge.target);
  }
  dagre.layout(graph);
  const result = new Map<string, Position>();
  for (const node of nodes) {
    const laid = graph.node(node.id);
    const width = node.width ?? DEFAULT_NODE_WIDTH;
    const height = node.height ?? DEFAULT_NODE_HEIGHT;
    result.set(node.id, { x: Math.round(laid.x - width / 2), y: Math.round(laid.y - height / 2) });
  }
  return result;
}
