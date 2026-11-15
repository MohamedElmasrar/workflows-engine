/** Coloured status pills for executions, nodes and workflows. */
import { Ban, CheckCircle2, CircleDashed, Clock, Loader2, SkipForward, TimerOff, XCircle } from "lucide-react";
import type { ExecutionStatus, NodeStatus, WorkflowStatus } from "@/lib/api/types";
import { humanize } from "@/lib/format";
import { executionStatusTone, nodeStatusTone, toneClasses, workflowStatusTone } from "@/lib/status";
import { cn } from "@/lib/utils";

const icons: Partial<Record<NodeStatus | ExecutionStatus, typeof Clock>> = {
  PENDING: Clock,
  READY: CircleDashed,
  QUEUED: CircleDashed,
  RUNNING: Loader2,
  COMPLETED: CheckCircle2,
  FAILED: XCircle,
  TIMED_OUT: TimerOff,
  CANCELLED: Ban,
  SKIPPED: SkipForward,
};

function Pill({ tone, label, icon: Icon, spin, className, testId }: {
  tone: string;
  label: string;
  icon?: typeof Clock;
  spin?: boolean;
  className?: string;
  testId?: string;
}) {
  return (
    <span
      data-testid={testId}
      className={cn("inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap", tone, className)}
    >
      {Icon ? <Icon className={cn("size-3", spin && "animate-spin")} aria-hidden /> : null}
      {label}
    </span>
  );
}

export function ExecutionStatusBadge({ status, className, testId }: { status: ExecutionStatus; className?: string; testId?: string }) {
  return (
    <Pill
      tone={toneClasses[executionStatusTone[status]]}
      label={humanize(status)}
      icon={icons[status]}
      spin={status === "RUNNING"}
      className={className}
      testId={testId}
    />
  );
}

export function NodeStatusBadge({ status, className }: { status: NodeStatus; className?: string }) {
  return (
    <Pill
      tone={toneClasses[nodeStatusTone[status]]}
      label={humanize(status)}
      icon={icons[status]}
      spin={status === "RUNNING"}
      className={className}
    />
  );
}

export function WorkflowStatusBadge({ status, className }: { status: WorkflowStatus; className?: string }) {
  return <Pill tone={toneClasses[workflowStatusTone[status]]} label={humanize(status)} className={className} />;
}
