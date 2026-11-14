"use client";

import { ArrowLeft, Ban, Loader2, Radio, RotateCcw, WifiOff } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { JsonViewer } from "@/components/json-viewer";
import { RelativeTime } from "@/components/relative-time";
import { describeError, ErrorState, LoadingState } from "@/components/states";
import { ExecutionStatusBadge, NodeStatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { isApiError } from "@/lib/api/client";
import { NODE_STATUSES, type HistoryEntry, type NodeStatus, type Execution } from "@/lib/api/types";
import { formatAbsolute, formatDuration, humanize } from "@/lib/format";
import { cn } from "@/lib/utils";
import { useWorkflow } from "@/features/workflows/hooks";
import { ExecutionGraph } from "./execution-graph";
import { useCancelExecution, useLiveExecution, useRetryExecution, type LiveState } from "./hooks";
import { NodeRunDetails } from "./node-run-details";

function LiveIndicator({ state }: { state: LiveState }) {
  const map: Record<LiveState, { label: string; className: string; icon: ReactNode }> = {
    idle: { label: "", className: "", icon: null },
    connecting: { label: "Connecting…", className: "text-muted-foreground", icon: <Loader2 className="size-3 animate-spin" /> },
    live: { label: "Live", className: "text-emerald-700", icon: <Radio className="size-3 animate-pulse" /> },
    reconnecting: { label: "Reconnecting…", className: "text-amber-700", icon: <WifiOff className="size-3" /> },
    ended: { label: "", className: "", icon: null },
    error: { label: "Live updates unavailable", className: "text-destructive", icon: <WifiOff className="size-3" /> },
  };
  const item = map[state];
  if (!item.label) return null;
  return (
    <span className={cn("flex items-center gap-1 text-xs", item.className)} role="status" data-testid="live-indicator">
      {item.icon}
      {item.label}
    </span>
  );
}

function Meta({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="truncate text-sm">{children}</dd>
    </div>
  );
}

const DETAIL_ORDER = ["errorCode", "errorMessage", "attempt", "nextAttemptAt", "workerId", "branch", "reason", "triggerType", "workflowVersion"];

function detailEntries(details: Record<string, unknown>): [string, string][] {
  const keys = Object.keys(details).sort((a, b) => {
    const ia = DETAIL_ORDER.indexOf(a);
    const ib = DETAIL_ORDER.indexOf(b);
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
  });
  return keys.map((k) => {
    const v = details[k];
    const text = typeof v === "string" ? (k.endsWith("At") ? formatAbsolute(v) : v) : JSON.stringify(v);
    return [k, text];
  });
}

function Timeline({ entries, onSelectNode, nodeNames }: { entries: HistoryEntry[]; onSelectNode: (id: string) => void; nodeNames: Map<string, string> }) {
  if (entries.length === 0) return <p className="text-sm text-muted-foreground">No events yet.</p>;
  return (
    <ol className="relative space-y-3 border-l pl-4" aria-label="Execution timeline" data-testid="timeline">
      {[...entries].reverse().map((entry) => {
        const status = entry.status && (NODE_STATUSES as readonly string[]).includes(entry.status) ? (entry.status as NodeStatus) : null;
        return (
          <li key={entry.sequence} className="relative" data-testid="timeline-entry">
            <span className="absolute top-1.5 -left-[21px] size-2.5 rounded-full border-2 border-background bg-muted-foreground" aria-hidden />
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[10px] text-muted-foreground">#{entry.sequence}</span>
              <span className="text-xs font-medium">{humanize(entry.type)}</span>
              {entry.nodeId ? (
                <button
                  type="button"
                  className="rounded bg-muted px-1.5 font-mono text-xs hover:bg-muted/70 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                  onClick={() => onSelectNode(entry.nodeId as string)}
                  title={nodeNames.get(entry.nodeId) ?? entry.nodeId}
                >
                  {entry.nodeId}
                </button>
              ) : null}
              {status ? <NodeStatusBadge status={status} /> : entry.status ? <span className="text-xs">{humanize(entry.status)}</span> : null}
            </div>
            <p className="text-xs text-muted-foreground" title={formatAbsolute(entry.occurredAt)}>
              {formatAbsolute(entry.occurredAt)}
            </p>
            {Object.keys(entry.details).length > 0 ? (
              <dl className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs">
                {detailEntries(entry.details).map(([k, v]) => (
                  <div key={k} className="flex gap-1">
                    <dt className="text-muted-foreground">{k}:</dt>
                    <dd className={cn("font-mono break-all", k === "errorCode" && "text-destructive")}>{v}</dd>
                  </div>
                ))}
              </dl>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}

function Header({ execution, liveState }: { execution: Execution; liveState: LiveState }) {
  const router = useRouter();
  const workflow = useWorkflow(execution.workflowId);
  const role = workflow.data?.role;
  const cancel = useCancelExecution(execution.id);
  const retry = useRetryExecution(execution.id);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const canOperate = role === "OWNER" || role === "EDITOR";
  const cancellable = execution.status === "PENDING" || execution.status === "RUNNING";
  const retryable = execution.status === "FAILED" || execution.status === "CANCELLED" || execution.status === "TIMED_OUT";

  const onCancel = async () => {
    try {
      await cancel.mutateAsync();
      toast.success("Execution cancelled");
    } catch (e) {
      const info = describeError(e);
      toast.error(isApiError(e) && e.status === 409 ? "The execution already finished" : info.title, { description: info.detail });
    } finally {
      setConfirmCancel(false);
    }
  };
  const onRetry = async () => {
    try {
      const next = await retry.mutateAsync();
      toast.success("Retry started");
      router.push(`/executions/${next.id}`);
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    }
  };

  return (
    <div className="space-y-4 border-b p-4 sm:px-6">
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="ghost" size="icon-sm" asChild>
          <Link href="/executions" aria-label="Back to executions">
            <ArrowLeft />
          </Link>
        </Button>
        <h1 className="text-lg font-semibold">
          <Link href={`/workflows/${execution.workflowId}`} className="hover:underline">
            {execution.workflowName}
          </Link>{" "}
          <span className="text-sm font-normal text-muted-foreground">
            <Link href={`/workflows/${execution.workflowId}?panel=versions`} className="hover:underline">
              v{execution.workflowVersion}
            </Link>
          </span>
        </h1>
        <ExecutionStatusBadge status={execution.status} testId="execution-status" />
        <LiveIndicator state={liveState} />
        <div className="ml-auto flex gap-2">
          {cancellable && canOperate ? (
            <Button variant="destructive" size="sm" onClick={() => setConfirmCancel(true)} disabled={cancel.isPending}>
              <Ban /> Cancel
            </Button>
          ) : null}
          {retryable && canOperate ? (
            <Button variant="outline" size="sm" onClick={() => void onRetry()} disabled={retry.isPending} data-testid="retry-execution">
              {retry.isPending ? <Loader2 className="animate-spin" /> : <RotateCcw />} Retry
            </Button>
          ) : null}
        </div>
      </div>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-3 lg:grid-cols-6">
        <Meta label="Trigger">
          {humanize(execution.triggerType)}
          {execution.retryOf ? (
            <>
              {" "}
              of{" "}
              <Link href={`/executions/${execution.retryOf}`} className="font-mono text-xs underline">
                {execution.retryOf.slice(0, 8)}
              </Link>
            </>
          ) : null}
        </Meta>
        <Meta label="Triggered by">
          <span title={execution.triggeredBy} className="font-mono text-xs">
            {execution.triggeredBy}
          </span>
        </Meta>
        <Meta label="Created">
          <RelativeTime value={execution.createdAt} />
        </Meta>
        <Meta label="Started">
          <RelativeTime value={execution.startedAt} />
        </Meta>
        <Meta label="Finished">
          <RelativeTime value={execution.finishedAt} />
        </Meta>
        <Meta label="Duration">{formatDuration(execution.durationMs)}</Meta>
      </dl>
      {execution.errorCode || execution.errorMessage ? (
        <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm" data-testid="execution-error">
          <span className="font-mono text-xs font-semibold text-destructive">{execution.errorCode}</span>
          {execution.errorMessage ? <p className="mt-0.5">{execution.errorMessage}</p> : null}
        </div>
      ) : null}
      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title="Cancel this execution?"
        description="Running and pending nodes are cancelled. Side effects that already happened are not undone."
        confirmLabel="Cancel execution"
        destructive
        pending={cancel.isPending}
        onConfirm={() => void onCancel()}
      />
    </div>
  );
}

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
      <Header execution={data} liveState={liveState} />
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
                <Timeline entries={events.data} onSelectNode={selectNode} nodeNames={nodeNames} />
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
