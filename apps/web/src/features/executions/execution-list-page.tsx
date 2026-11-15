"use client";
/** Execution history page: URL-driven filters, the execution table and pagination. */
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { PaginationBar } from "@/components/pagination-bar";
import { usePreferences } from "@/lib/preferences";
import { useUrlState } from "@/lib/url-state";
import { ExecutionFilters } from "./components/execution-filters";
import { ExecutionTable } from "./components/execution-table";
import { useExecutionList } from "./hooks/execution-queries";

export function ExecutionListPage() {
  const { get, update } = useUrlState();
  const [prefs] = usePreferences();
  const workflowId = get("workflowId");
  const status = get("status");
  const from = get("from");
  const to = get("to");
  const page = Math.max(0, Number(get("page")) || 0);

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
      <ExecutionFilters workflowId={workflowId} status={status} from={from} to={to} hasFilters={hasFilters} update={update} />
      <ExecutionTable list={list} hasFilters={hasFilters} />
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
