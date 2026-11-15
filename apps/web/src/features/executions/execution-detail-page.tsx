"use client";
/** Execution detail: live graph on the left, timeline / node / input-output tabs on the right. */
import { useState } from "react";
import { ErrorState } from "@/components/error-state";
import { JsonViewer } from "@/components/json-viewer";
import { LoadingState } from "@/components/loading-state";
import { NodeStatusBadge } from "@/components/status-badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { isApiError } from "@/lib/api/api-error";
import { ExecutionGraph } from "./components/execution-graph";
import { ExecutionHeader } from "./components/execution-header";
import { ExecutionTimeline } from "./components/execution-timeline";
import { NodeRunDetails } from "./components/node-run-details";
import { useLiveExecution } from "./hooks/use-live-execution";

export function ExecutionDetailPage({ executionId }: { executionId: string }) {
  const { execution, events, liveState } = useLiveExecution(executionId);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [tab, setTab] = useState("timeline");

  if (execution.isPending) return <LoadingState label="Loading execution…" className="h-full" />;
  if (execution.isError) {
    return (
      <div className="p-8">
        <ErrorState
          error={execution.error}
          title={isApiError(execution.error) && execution.error.status === 404 ? "Execution not found" : undefined}
          onRetry={() => void execution.refetch()}
        />
      </div>
    );
  }
  const data = execution.data;
  const selected = selectedNodeId ? data.nodes.find((n) => n.nodeId === selectedNodeId) : undefined;
  const nodeNames = new Map(data.nodes.map((n) => [n.nodeId, n.name]));
  const selectNode = (id: string | null) => {
    setSelectedNodeId(id);
    if (id) setTab("node");
  };

  return (
    <div className="flex h-full flex-col">
      <ExecutionHeader execution={data} liveState={liveState} />
      <div className="grid min-h-0 flex-1 grid-cols-1 grid-rows-[minmax(18rem,1fr)_auto] lg:grid-cols-[minmax(0,1fr)_28rem] lg:grid-rows-1">
        <div className="relative min-h-[18rem]">
          <ExecutionGraph definition={data.definition} nodeExecutions={data.nodes} selectedNodeId={selectedNodeId} onSelectNode={selectNode} />
        </div>
        <aside className="min-h-0 overflow-y-auto border-t p-4 lg:border-t-0 lg:border-l" aria-label="Execution details">
          <Tabs value={tab} onValueChange={setTab}>
            <TabsList className="w-full">
              <TabsTrigger value="timeline">Timeline</TabsTrigger>
              <TabsTrigger value="node">Node</TabsTrigger>
              <TabsTrigger value="data">Input / output</TabsTrigger>
            </TabsList>
            <TabsContent value="timeline" className="pt-4">
              {events.isPending ? (
                <LoadingState />
              ) : events.isError ? (
                <ErrorState error={events.error} onRetry={() => void events.refetch()} />
              ) : (
                <ExecutionTimeline entries={events.data} onSelectNode={selectNode} nodeNames={nodeNames} />
              )}
            </TabsContent>
            <TabsContent value="node" className="pt-4">
              {selected ? (
                <div className="space-y-3">
                  <div>
                    <h2 className="font-semibold">{selected.name}</h2>
                    <p className="font-mono text-xs text-muted-foreground">{selected.nodeId}</p>
                  </div>
                  <NodeRunDetails node={selected} />
                </div>
              ) : (
                <div className="space-y-2">
                  <p className="text-sm text-muted-foreground">Select a node in the graph to see its attempts, input and output.</p>
                  <ul className="divide-y rounded-md border">
                    {data.nodes.map((n) => (
                      <li key={n.nodeId}>
                        <button
                          type="button"
                          className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-muted/50 focus-visible:bg-muted focus-visible:outline-none"
                          onClick={() => selectNode(n.nodeId)}
                        >
                          <span className="truncate">{n.name}</span>
                          <NodeStatusBadge status={n.status} />
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </TabsContent>
            <TabsContent value="data" className="space-y-4 pt-4">
              <section>
                <h3 className="mb-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">Input</h3>
                <JsonViewer value={data.input} emptyLabel="No input" />
              </section>
              <section>
                <h3 className="mb-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">Output</h3>
                <JsonViewer value={data.output} emptyLabel="No output yet" />
              </section>
              <p className="text-xs text-muted-foreground">Sensitive values (tokens, passwords, API keys…) are masked by the server.</p>
            </TabsContent>
          </Tabs>
        </aside>
      </div>
    </div>
  );
}
