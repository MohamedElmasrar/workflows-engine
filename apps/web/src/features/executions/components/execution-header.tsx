"use client";
/** Execution title, status, live indicator, metadata, error banner and the cancel/retry actions. */
import { ArrowLeft, Ban, Loader2, RotateCcw } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { describeError } from "@/components/describe-error";
import { RelativeTime } from "@/components/relative-time";
import { ExecutionStatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { useWorkflow } from "@/features/workflows/hooks/workflow-queries";
import { isApiError } from "@/lib/api/api-error";
import type { Execution } from "@/lib/api/types";
import { formatDuration, humanize } from "@/lib/format";
import { useCancelExecution, useRetryExecution } from "../hooks/execution-mutations";
import type { LiveState } from "../hooks/use-live-execution";
import { LiveIndicator } from "./live-indicator";
import { MetaItem } from "./meta-item";

export function ExecutionHeader({ execution, liveState }: { execution: Execution; liveState: LiveState }) {
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
        <MetaItem label="Trigger">
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
        </MetaItem>
        <MetaItem label="Triggered by">
          <span title={execution.triggeredBy} className="font-mono text-xs">
            {execution.triggeredBy}
          </span>
        </MetaItem>
        <MetaItem label="Created">
          <RelativeTime value={execution.createdAt} />
        </MetaItem>
        <MetaItem label="Started">
          <RelativeTime value={execution.startedAt} />
        </MetaItem>
        <MetaItem label="Finished">
          <RelativeTime value={execution.finishedAt} />
        </MetaItem>
        <MetaItem label="Duration">{formatDuration(execution.durationMs)}</MetaItem>
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
