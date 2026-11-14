"use client";

import { Handle, Position, type NodeProps, type NodeTypes } from "@xyflow/react";
import { AlertCircle } from "lucide-react";
import { createContext, memo, useContext } from "react";
import { NodeStatusBadge } from "@/components/status-badge";
import type { NodeStatus } from "@/lib/api/types";
import { formatSeconds } from "@/lib/format";
import { nodeStatusRing } from "@/lib/status";
import { cn } from "@/lib/utils";
import type { WorkflowFlowNode } from "../flow-types";
import { NODE_CATALOG } from "../node-catalog";

/** Per-graph decorations supplied by the editor or the execution view. */
export interface GraphDecorations {
  statusById?: Record<string, NodeStatus>;
  issuesById?: Record<string, string[]>;
  layoutDirection?: "LR" | "TB";
}

export const GraphDecorationsContext = createContext<GraphDecorations>({});

function useDecorations(id: string) {
  const ctx = useContext(GraphDecorationsContext);
  return {
    status: ctx.statusById?.[id],
    issues: ctx.issuesById?.[id],
    vertical: ctx.layoutDirection === "TB",
  };
}

function subtitle(node: WorkflowFlowNode["data"]): string {
  const c = node.config;
  switch (node.nodeType) {
    case "HTTP_REQUEST":
      return `${String(c.method ?? "GET")} ${String(c.url ?? "")}`;
    case "SCHEDULE_TRIGGER":
      return `${String(c.cron ?? "")} · ${String(c.timezone ?? "")}`;
    case "DELAY":
      return typeof c.durationSeconds === "number" ? `Wait ${formatSeconds(c.durationSeconds)}` : "";
    case "LOG":
      return String(c.message ?? "");
    case "NOTIFICATION":
      return `${String(c.channel ?? "")} → ${String(c.recipient ?? "")}`;
    case "CONDITION": {
      const n = Array.isArray(c.conditions) ? c.conditions.length : 0;
      return `${n} condition${n === 1 ? "" : "s"} (${String(c.combinator ?? "AND")})`;
    }
    case "WEBHOOK_TRIGGER":
      return c.requireSignature ? "Signed webhook" : "Webhook";
    case "MANUAL_TRIGGER":
      return "Run on demand";
    default:
      return NODE_CATALOG[node.nodeType].description;
  }
}

const categoryStyles = {
  trigger: { container: "rounded-l-full pl-3 border-violet-300 bg-violet-50/60 dark:bg-violet-950/40", icon: "bg-violet-600 text-white" },
  action: { container: "border-border bg-card", icon: "bg-sky-100 text-sky-700 dark:bg-sky-900 dark:text-sky-200" },
  condition: { container: "border-amber-300 bg-amber-50/50 dark:bg-amber-950/30", icon: "bg-amber-500 text-white" },
  delay: { container: "border-dashed border-slate-400 bg-card", icon: "bg-slate-200 text-slate-700 dark:bg-slate-700 dark:text-slate-100" },
} as const;

function NodeShell({ id, data, selected, kind }: NodeProps<WorkflowFlowNode> & { kind: keyof typeof categoryStyles }) {
  const { status, issues, vertical } = useDecorations(id);
  const entry = NODE_CATALOG[data.nodeType];
  const Icon = entry.icon;
  const style = categoryStyles[kind];
  const hasIssues = Boolean(issues && issues.length);
  const targetPos = vertical ? Position.Top : Position.Left;
  const sourcePos = vertical ? Position.Bottom : Position.Right;
  return (
    <div
      data-testid={`flow-node-${id}`}
      data-node-type={data.nodeType}
      data-status={status}
      aria-label={`${entry.label} node ${data.name}${status ? `, status ${status}` : ""}${hasIssues ? ", has errors" : ""}`}
      className={cn(
        "relative w-60 rounded-lg border-2 px-3 py-2.5 shadow-sm transition-shadow",
        style.container,
        selected && "outline-2 outline-offset-2 outline-primary",
        hasIssues && "border-destructive",
        status && nodeStatusRing[status],
      )}
    >
      {kind !== "trigger" ? <Handle type="target" position={targetPos} id={null} aria-label="Input" /> : null}
      <div className="flex items-center gap-2.5">
        <span className={cn("flex size-8 shrink-0 items-center justify-center rounded-md", style.icon)} aria-hidden>
          <Icon className="size-4" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm leading-tight font-medium" title={data.name}>
            {data.name}
          </p>
          <p className="truncate text-xs text-muted-foreground" title={subtitle(data)}>
            {subtitle(data)}
          </p>
        </div>
        {hasIssues ? (
          <span title={issues?.join("\n")} className="text-destructive" data-testid={`node-error-${id}`}>
            <AlertCircle className="size-4" aria-label="Has validation errors" />
          </span>
        ) : null}
      </div>
      {status ? (
        <div className="absolute -top-3 right-2">
          <NodeStatusBadge status={status} className="bg-background" />
        </div>
      ) : null}
      {kind === "condition" ? (
        <>
          <Handle
            type="source"
            id="true"
            position={sourcePos}
            style={vertical ? { left: "30%" } : { top: "30%" }}
            className="!bg-emerald-600"
            aria-label="True branch"
            data-testid={`handle-${id}-true`}
          />
          <Handle
            type="source"
            id="false"
            position={sourcePos}
            style={vertical ? { left: "70%" } : { top: "70%" }}
            className="!bg-red-600"
            aria-label="False branch"
            data-testid={`handle-${id}-false`}
          />
          <span
            className={cn("pointer-events-none absolute text-[10px] font-semibold text-emerald-700", vertical ? "-bottom-4 left-[22%]" : "top-[22%] -right-8")}
            aria-hidden
          >
            true
          </span>
          <span
            className={cn("pointer-events-none absolute text-[10px] font-semibold text-red-700", vertical ? "-bottom-4 left-[62%]" : "top-[62%] -right-9")}
            aria-hidden
          >
            false
          </span>
        </>
      ) : (
        <Handle type="source" position={sourcePos} id={null} aria-label="Output" />
      )}
    </div>
  );
}

const TriggerNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="trigger" />);
TriggerNode.displayName = "TriggerNode";
const ActionNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="action" />);
ActionNode.displayName = "ActionNode";
const ConditionNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="condition" />);
ConditionNode.displayName = "ConditionNode";
const DelayNode = memo((props: NodeProps<WorkflowFlowNode>) => <NodeShell {...props} kind="delay" />);
DelayNode.displayName = "DelayNode";

export const nodeTypes: NodeTypes = {
  trigger: TriggerNode,
  action: ActionNode,
  condition: ConditionNode,
  delay: DelayNode,
};
