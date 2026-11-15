"use client";

import { Activity } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId } from "react";
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { PaginationBar } from "@/components/pagination-bar";
import { RelativeTime } from "@/components/relative-time";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { TableSkeleton } from "@/components/table-skeleton";
import { ExecutionStatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EXECUTION_STATUSES } from "@/lib/api/types";
import { formatDuration, humanize } from "@/lib/format";
import { usePreferences } from "@/lib/preferences";
import { useUrlState } from "@/lib/url-state";
import { useWorkflowList } from "@/features/workflows/hooks";
import { useExecutionList } from "./hooks";

/** `datetime-local` value (local time) ↔ ISO instant. */
function toLocalInput(iso: string): string {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return "";
  const d = new Date(t);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInput(value: string): string | null {
  if (!value) return null;
  const t = Date.parse(value);
  return Number.isNaN(t) ? null : new Date(t).toISOString();
}

export function ExecutionListPage() {
  const router = useRouter();
  const { get, update } = useUrlState();
  const [prefs] = usePreferences();
  const workflowId = get("workflowId");
  const status = get("status");
  const from = get("from");
  const to = get("to");
  const page = Math.max(0, Number(get("page")) || 0);
  const fromId = useId();
  const toId = useId();

  const workflows = useWorkflowList({ size: 100, sort: "name", direction: "asc" });
  const list = useExecutionList({
    workflowId: workflowId || undefined,
    status: status || undefined,
    from: from || undefined,
    to: to || undefined,
    page,
    size: prefs.pageSize,
  });
  const hasFilters = Boolean(workflowId || status || from || to);

  return (
    <PageContainer>
      <PageHeader title="Executions" description="History of workflow runs across all workflows you can access." />
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
      <div className="rounded-lg border">
        {list.isPending ? (
          <TableSkeleton columns={7} />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => void list.refetch()} className="m-4" />
        ) : list.data.items.length === 0 ? (
          <EmptyState
            className="m-4"
            icon={<Activity className="size-5" />}
            title={hasFilters ? "No executions match your filters" : "No executions yet"}
            description={hasFilters ? "Adjust the filters or the date range." : "Run a published workflow to see it here."}
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Status</TableHead>
                <TableHead>Workflow</TableHead>
                <TableHead className="hidden sm:table-cell">Version</TableHead>
                <TableHead className="hidden md:table-cell">Trigger</TableHead>
                <TableHead>Started</TableHead>
                <TableHead className="hidden md:table-cell">Duration</TableHead>
                <TableHead className="hidden lg:table-cell">Error</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.data.items.map((e) => (
                <TableRow
                  key={e.id}
                  className="cursor-pointer"
                  onClick={() => router.push(`/executions/${e.id}`)}
                  data-testid="execution-row"
                >
                  <TableCell>
                    <ExecutionStatusBadge status={e.status} />
                  </TableCell>
                  <TableCell className="max-w-56 truncate">
                    <Link href={`/executions/${e.id}`} className="font-medium hover:underline" onClick={(ev) => ev.stopPropagation()}>
                      {e.workflowName}
                    </Link>
                  </TableCell>
                  <TableCell className="hidden text-sm sm:table-cell">v{e.workflowVersion}</TableCell>
                  <TableCell className="hidden text-sm md:table-cell">{humanize(e.triggerType)}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    <RelativeTime value={e.startedAt ?? e.createdAt} />
                  </TableCell>
                  <TableCell className="hidden text-sm md:table-cell">{formatDuration(e.durationMs)}</TableCell>
                  <TableCell className="hidden max-w-64 truncate text-xs lg:table-cell" title={e.errorMessage ?? undefined}>
                    {e.errorCode ? <span className="font-mono text-destructive">{e.errorCode}</span> : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
      {list.data ? (
        <PaginationBar
          page={list.data.page}
          size={list.data.size}
          totalItems={list.data.totalItems}
          totalPages={list.data.totalPages}
          onPageChange={(p) => update({ page: p || null }, { resetPage: false })}
        />
      ) : null}
    </PageContainer>
  );
}
