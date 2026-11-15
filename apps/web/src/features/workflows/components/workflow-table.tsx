/** Workflow list table with sortable headers, row actions and its loading, error and empty states. */
import { Waypoints } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { RelativeTime } from "@/components/relative-time";
import { WorkflowStatusBadge } from "@/components/status-badge";
import { TableSkeleton } from "@/components/table-skeleton";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { WorkflowSummary } from "@/lib/api/types";
import { humanize } from "@/lib/format";
import { useWorkflowList } from "../hooks/workflow-queries";
import { CreateWorkflowDialog } from "./create-workflow-dialog";
import { type SortField, SortHeader } from "./sort-header";
import { WorkflowRowActions } from "./workflow-row-actions";

export function WorkflowTable({
  list,
  hasFilters,
  onClearFilters,
  sort,
  direction,
  onSort,
  onDuplicate,
  onArchive,
}: {
  list: ReturnType<typeof useWorkflowList>;
  hasFilters: boolean;
  onClearFilters: () => void;
  sort: SortField;
  direction: "asc" | "desc";
  onSort: (field: SortField) => void;
  onDuplicate: (workflow: WorkflowSummary) => void;
  onArchive: (workflow: WorkflowSummary) => void;
}) {
  return (
    <div className="rounded-lg border">
      {list.isPending ? (
        <TableSkeleton columns={6} />
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} className="m-4" />
      ) : list.data.items.length === 0 ? (
        <EmptyState
          className="m-4"
          icon={<Waypoints className="size-5" />}
          title={hasFilters ? "No workflows match your filters" : "No workflows yet"}
          description={hasFilters ? "Try another search or status." : "Create your first workflow to get started."}
          action={hasFilters ? <Button variant="outline" onClick={onClearFilters}>Clear filters</Button> : <CreateWorkflowDialog />}
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <SortHeader field="name" label="Name" sort={sort} direction={direction} onSort={onSort} />
              <TableHead>Status</TableHead>
              <TableHead className="hidden md:table-cell">Trigger</TableHead>
              <TableHead className="hidden sm:table-cell">Version</TableHead>
              <TableHead className="hidden lg:table-cell">Nodes</TableHead>
              <TableHead className="hidden lg:table-cell">Role</TableHead>
              <SortHeader field="updatedAt" label="Updated" sort={sort} direction={direction} onSort={onSort} />
              <SortHeader field="createdAt" label="Created" sort={sort} direction={direction} onSort={onSort} />
              <TableHead className="w-10">
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.data.items.map((w) => (
              <TableRow key={w.id} data-testid="workflow-row" className={list.isPlaceholderData ? "opacity-60" : undefined}>
                <TableCell className="max-w-xs">
                  <Link href={`/workflows/${w.id}`} className="font-medium hover:underline focus-visible:underline focus-visible:outline-none">
                    {w.name}
                  </Link>
                  {w.description ? <p className="truncate text-xs text-muted-foreground" title={w.description}>{w.description}</p> : null}
                </TableCell>
                <TableCell>
                  <WorkflowStatusBadge status={w.status} />
                  {w.hasUnpublishedChanges && w.latestVersion ? <span className="ml-1 text-xs text-amber-700" title="Draft has unpublished changes">•</span> : null}
                </TableCell>
                <TableCell className="hidden text-sm md:table-cell">{w.triggerType ? humanize(w.triggerType.replace(/_TRIGGER$/, "")) : "—"}</TableCell>
                <TableCell className="hidden text-sm sm:table-cell">{w.latestVersion ? `v${w.latestVersion}` : "—"}</TableCell>
                <TableCell className="hidden text-sm lg:table-cell">{w.nodeCount}</TableCell>
                <TableCell className="hidden text-sm lg:table-cell">{humanize(w.role)}</TableCell>
                <TableCell className="text-sm text-muted-foreground"><RelativeTime value={w.updatedAt} /></TableCell>
                <TableCell className="text-sm text-muted-foreground"><RelativeTime value={w.createdAt} /></TableCell>
                <TableCell>
                  <WorkflowRowActions workflow={w} onDuplicate={onDuplicate} onArchive={onArchive} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
