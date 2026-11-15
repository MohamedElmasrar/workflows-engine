"use client";
/** Template references for a node, recomputed only when node ids, names, types or edges change. */
import { useMemo } from "react";
import { useShallow } from "zustand/react/shallow";
import type { NodeType } from "@/lib/api/types";
import { useEditorStore } from "@/stores/editor/editor-store";
import { buildReferences, type TemplateReference } from "../lib/template-references";

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
