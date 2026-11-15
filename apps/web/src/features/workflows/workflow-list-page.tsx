"use client";

import { ArrowDown, ArrowUp, ArrowUpDown, Copy, MoreHorizontal, Pencil, Archive, Search, Waypoints } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { PaginationBar } from "@/components/pagination-bar";
import { RelativeTime } from "@/components/relative-time";
import { describeError } from "@/components/describe-error";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { TableSkeleton } from "@/components/table-skeleton";
import { WorkflowStatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { WorkflowSummary } from "@/lib/api/types";
import { humanize } from "@/lib/format";
import { useDebouncedValue } from "@/lib/hooks";
import { usePreferences } from "@/lib/preferences";
import { useUrlState } from "@/lib/url-state";
import { CreateWorkflowDialog } from "./create-workflow-dialog";
import { useArchiveWorkflow, useDuplicateWorkflow, useWorkflowList } from "./hooks";

const SORTS = ["name", "updatedAt", "createdAt"] as const;
type SortField = (typeof SORTS)[number];

function SortHeader({ field, label, sort, direction, onSort }: {
  field: SortField;
  label: string;
  sort: SortField;
  direction: "asc" | "desc";
  onSort: (field: SortField) => void;
}) {
  const active = sort === field;
  const Icon = !active ? ArrowUpDown : direction === "asc" ? ArrowUp : ArrowDown;
  return (
    <TableHead aria-sort={active ? (direction === "asc" ? "ascending" : "descending") : "none"}>
      <button
        type="button"
        onClick={() => onSort(field)}
        className="inline-flex items-center gap-1 rounded hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
      >
        {label}
        <Icon className="size-3.5" aria-hidden />
      </button>
    </TableHead>
  );
}

export function WorkflowListPage() {
  const router = useRouter();
  const { get, update } = useUrlState();
  const [prefs] = usePreferences();
  const status = get("status");
  const sort: SortField = (SORTS as readonly string[]).includes(get("sort")) ? (get("sort") as SortField) : "updatedAt";
  const direction = get("direction") === "asc" ? "asc" : "desc";
  const page = Math.max(0, Number(get("page")) || 0);
  const urlSearch = get("search");

  const [search, setSearch] = useState(urlSearch);
  const debounced = useDebouncedValue(search, 300);
  useEffect(() => {
    if (debounced !== urlSearch) update({ search: debounced });
  }, [debounced]); // eslint-disable-line react-hooks/exhaustive-deps

  const list = useWorkflowList({ search: urlSearch || undefined, status: status || undefined, sort, direction, page, size: prefs.pageSize });
  const duplicate = useDuplicateWorkflow();
  const archive = useArchiveWorkflow();
  const [archiving, setArchiving] = useState<WorkflowSummary | null>(null);

  const onSort = (field: SortField) => {
    if (field === sort) update({ direction: direction === "asc" ? "desc" : "asc" });
    else update({ sort: field, direction: field === "name" ? "asc" : "desc" });
  };

  const onDuplicate = async (workflow: WorkflowSummary) => {
    try {
      const copy = await duplicate.mutateAsync(workflow.id);
      toast.success(`Duplicated as “${copy.name}”`, {
        action: { label: "Open", onClick: () => router.push(`/workflows/${copy.id}`) },
      });
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    }
  };

  const onArchive = async () => {
    if (!archiving) return;
    try {
      await archive.mutateAsync(archiving.id);
      toast.success(`Archived “${archiving.name}”`);
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    } finally {
      setArchiving(null);
    }
  };

  const hasFilters = Boolean(urlSearch || status);
  return (
    <PageContainer>
      <PageHeader title="Workflows" description="Design, publish and run automation workflows." actions={<CreateWorkflowDialog />} />
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-sm">
          <Search className="absolute top-2 left-2.5 size-4 text-muted-foreground" aria-hidden />
          <Input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or description"
            aria-label="Search workflows"
            className="pl-8"
          />
        </div>
        <Select value={status || "ALL"} onValueChange={(v) => update({ status: v === "ALL" ? null : v })}>
          <SelectTrigger className="w-full sm:w-44" aria-label="Filter by status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All statuses</SelectItem>
            <SelectItem value="DRAFT">Draft</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="ARCHIVED">Archived</SelectItem>
          </SelectContent>
        </Select>
      </div>
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
            action={hasFilters ? <Button variant="outline" onClick={() => { setSearch(""); update({ search: null, status: null }); }}>Clear filters</Button> : <CreateWorkflowDialog />}
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
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${w.name}`}>
                          <MoreHorizontal />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => router.push(`/workflows/${w.id}`)}>
                          <Pencil /> {w.role === "VIEWER" ? "View" : "Edit"}
                        </DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => void onDuplicate(w)}>
                          <Copy /> Duplicate
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          variant="destructive"
                          disabled={w.role !== "OWNER" || w.status === "ARCHIVED"}
                          onSelect={() => setArchiving(w)}
                        >
                          <Archive /> Archive{w.role !== "OWNER" ? " (owner only)" : ""}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
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
      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(o) => !o && setArchiving(null)}
        title={`Archive “${archiving?.name ?? ""}”?`}
        description="Archived workflows stop accepting webhook and schedule triggers and are hidden from the list. Past executions remain available."
        confirmLabel="Archive"
        destructive
        pending={archive.isPending}
        onConfirm={() => void onArchive()}
      />
    </PageContainer>
  );
}
