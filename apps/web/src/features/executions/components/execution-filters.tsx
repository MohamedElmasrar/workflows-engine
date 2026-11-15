"use client";
/** Execution list filters (workflow, status, date range) bound to URL search params. */
import { useId } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useWorkflowList } from "@/features/workflows/hooks";
import { EXECUTION_STATUSES } from "@/lib/api/types";
import { humanize } from "@/lib/format";
import { useUrlState } from "@/lib/url-state";
import { fromLocalInput, toLocalInput } from "../lib/datetime-input";

type ExecutionFilterValues = { workflowId: string; status: string; from: string; to: string };

export function ExecutionFilters({
  workflowId,
  status,
  from,
  to,
  hasFilters,
  update,
}: ExecutionFilterValues & { hasFilters: boolean; update: ReturnType<typeof useUrlState>["update"] }) {
  const fromId = useId();
  const toId = useId();
  const workflows = useWorkflowList({ size: 100, sort: "name", direction: "asc" });

  return (
    <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-[16rem_12rem_auto_auto_auto] lg:items-end">
      <div className="space-y-1">
        <span className="text-xs font-medium text-muted-foreground">Workflow</span>
        <Select value={workflowId || "ALL"} onValueChange={(v) => update({ workflowId: v === "ALL" ? null : v })}>
          <SelectTrigger className="w-full" aria-label="Filter by workflow">
            <SelectValue placeholder="All workflows" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All workflows</SelectItem>
            {workflows.data?.items.map((w) => (
              <SelectItem key={w.id} value={w.id}>
                {w.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <span className="text-xs font-medium text-muted-foreground">Status</span>
        <Select value={status || "ALL"} onValueChange={(v) => update({ status: v === "ALL" ? null : v })}>
          <SelectTrigger className="w-full" aria-label="Filter by status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All statuses</SelectItem>
            {EXECUTION_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {humanize(s)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1">
        <label htmlFor={fromId} className="text-xs font-medium text-muted-foreground">
          From
        </label>
        <Input id={fromId} type="datetime-local" value={from ? toLocalInput(from) : ""} onChange={(e) => update({ from: fromLocalInput(e.target.value) })} />
      </div>
      <div className="space-y-1">
        <label htmlFor={toId} className="text-xs font-medium text-muted-foreground">
          To
        </label>
        <Input id={toId} type="datetime-local" value={to ? toLocalInput(to) : ""} onChange={(e) => update({ to: fromLocalInput(e.target.value) })} />
      </div>
      {hasFilters ? (
        <Button variant="ghost" onClick={() => update({ workflowId: null, status: null, from: null, to: null })}>
          Clear filters
        </Button>
      ) : null}
    </div>
  );
}
