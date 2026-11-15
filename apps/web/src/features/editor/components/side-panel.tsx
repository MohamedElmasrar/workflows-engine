"use client";
/** Editor right panel: node config, edge details or workflow settings, depending on the selection. */
import { ArrowRight, Trash2 } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Execution } from "@/lib/api/types";
import { useEditorStore } from "@/stores/editor/editor-store";
import { NodeRunDetails } from "@/features/executions/components/node-run-details";
import { NodeConfigPanel } from "./node-config-panel";
import { WorkflowSettingsPanel } from "./workflow-settings-panel";

function EdgePanel({ edgeId, readOnly }: { edgeId: string; readOnly: boolean }) {
  const edge = useEditorStore((s) => s.edges.find((e) => e.id === edgeId));
  const nodes = useEditorStore((s) => s.nodes);
  const deleteEdge = useEditorStore((s) => s.deleteEdge);
  if (!edge) return null;
  const name = (id: string) => nodes.find((n) => n.id === id)?.data.name ?? id;
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-sm font-semibold">Connection</h2>
        <p className="font-mono text-xs text-muted-foreground">id: {edge.id}</p>
      </div>
      <p className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-medium">{name(edge.source)}</span>
        {edge.sourceHandle ? <span className="rounded border px-1.5 text-xs">{edge.sourceHandle}</span> : null}
        <ArrowRight className="size-4" aria-hidden />
        <span className="font-medium">{name(edge.target)}</span>
      </p>
      {!readOnly ? (
        <Button variant="destructive" size="sm" onClick={() => deleteEdge(edge.id)}>
          <Trash2 /> Delete connection
        </Button>
      ) : null}
    </div>
  );
}

export function SidePanel({ lastRun }: { lastRun: Execution | null | undefined }) {
  const selectedEdgeId = useEditorStore((s) => s.selectedEdgeId);
  const node = useEditorStore((s) => (s.selectedNodeId ? s.nodes.find((n) => n.id === s.selectedNodeId) : undefined));
  const readOnly = useEditorStore((s) => s.readOnly);
  const sessionKey = useEditorStore((s) => s.sessionKey);

  let content;
  if (node) {
    const config = <NodeConfigPanel key={`${sessionKey}:${node.id}:${node.data.nodeType}`} node={node} readOnly={readOnly} />;
    if (lastRun !== undefined) {
      const run = lastRun?.nodes.find((n) => n.nodeId === node.id);
      content = (
        <Tabs defaultValue="configure">
          <TabsList className="w-full">
            <TabsTrigger value="configure">Configure</TabsTrigger>
            <TabsTrigger value="last-run">Last run</TabsTrigger>
          </TabsList>
          <TabsContent value="configure" className="pt-3">
            {config}
          </TabsContent>
          <TabsContent value="last-run" className="pt-3">
            {!lastRun ? (
              <p className="text-sm text-muted-foreground">This workflow has not run yet.</p>
            ) : run ? (
              <div className="space-y-3">
                <NodeRunDetails node={run} />
                <Button variant="link" size="sm" asChild className="px-0">
                  <Link href={`/executions/${lastRun.id}`}>Open execution</Link>
                </Button>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">This node was not part of the last run (v{lastRun.workflowVersion}).</p>
            )}
          </TabsContent>
        </Tabs>
      );
    } else {
      content = config;
    }
  } else if (selectedEdgeId) {
    content = <EdgePanel edgeId={selectedEdgeId} readOnly={readOnly} />;
  } else {
    content = <WorkflowSettingsPanel key={sessionKey} readOnly={readOnly} />;
  }

  return (
    <aside aria-label="Properties" className="h-full overflow-y-auto border-l bg-background p-4">
      {content}
    </aside>
  );
}
