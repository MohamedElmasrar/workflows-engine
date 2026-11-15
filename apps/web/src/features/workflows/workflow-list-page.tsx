"use client";
/** Workflow list page: search and status filters, sortable table, pagination and the archive confirmation. */
import { ConfirmDialog } from "@/components/confirm-dialog";
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { PaginationBar } from "@/components/pagination-bar";
import { usePreferences } from "@/lib/preferences";
import { CreateWorkflowDialog } from "./components/create-workflow-dialog";
import { WorkflowFilters } from "./components/workflow-filters";
import { WorkflowTable } from "./components/workflow-table";
import { useWorkflowListParams } from "./hooks/use-workflow-list-params";
import { useWorkflowRowActions } from "./hooks/use-workflow-row-actions";
import { useWorkflowList } from "./hooks/workflow-queries";

export function WorkflowListPage() {
  const [prefs] = usePreferences();
  const { update, status, sort, direction, page, urlSearch, search, setSearch, onSort } = useWorkflowListParams();
  const list = useWorkflowList({ search: urlSearch || undefined, status: status || undefined, sort, direction, page, size: prefs.pageSize });
  const { archive, archiving, setArchiving, onDuplicate, onArchive } = useWorkflowRowActions();

  const hasFilters = Boolean(urlSearch || status);
  return (
    <PageContainer>
      <PageHeader title="Workflows" description="Design, publish and run automation workflows." actions={<CreateWorkflowDialog />} />
      <WorkflowFilters search={search} onSearchChange={setSearch} status={status} onStatusChange={(s) => update({ status: s })} />
      <WorkflowTable
        list={list}
        hasFilters={hasFilters}
        onClearFilters={() => {
          setSearch("");
          update({ search: null, status: null });
        }}
        sort={sort}
        direction={direction}
        onSort={onSort}
        onDuplicate={(w) => void onDuplicate(w)}
        onArchive={setArchiving}
      />
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
