"use client";
/** Execution list table with its loading, error and empty states; rows open the execution. */
import { Activity } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { RelativeTime } from "@/components/relative-time";
import { ExecutionStatusBadge } from "@/components/status-badge";
import { TableSkeleton } from "@/components/table-skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatDuration, humanize } from "@/lib/format";
import { useExecutionList } from "../hooks/execution-queries";

export function ExecutionTable({ list, hasFilters }: { list: ReturnType<typeof useExecutionList>; hasFilters: boolean }) {
  const router = useRouter();
  return (
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
  );
}
