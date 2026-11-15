"use client";
/** Dashboard: workflow and execution stat cards plus recent activity. */
import { Activity, CheckCircle2, PlayCircle, Waypoints, XCircle, Zap } from "lucide-react";
import { ErrorState } from "@/components/error-state";
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { useExecutionStats } from "@/features/executions/hooks/execution-queries";
import { useWorkflowStats } from "@/features/workflows/hooks/workflow-queries";
import { useAuth } from "@/lib/auth/auth-provider";
import { RecentActivityCard } from "./components/recent-activity-card";
import { StatCard } from "./components/stat-card";

function todayIso(): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.toISOString();
}

export function DashboardPage() {
  const { user } = useAuth();
  const workflows = useWorkflowStats();
  const executions = useExecutionStats();
  const today = encodeURIComponent(todayIso());

  return (
    <PageContainer>
      <PageHeader title={`Welcome back, ${user.name?.split(" ")[0] ?? user.username}`} description="An overview of your workflows and today's executions." />
      {workflows.isError || executions.isError ? (
        <ErrorState
          className="mb-6"
          error={workflows.error ?? executions.error}
          onRetry={() => {
            void workflows.refetch();
            void executions.refetch();
          }}
        />
      ) : null}
      <section aria-label="Statistics" className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
        <StatCard title="Total workflows" value={workflows.data?.totalWorkflows} loading={workflows.isPending} icon={<Waypoints className="size-4" />} href="/workflows" />
        <StatCard title="Active workflows" value={workflows.data?.activeWorkflows} loading={workflows.isPending} icon={<Zap className="size-4" />} href="/workflows?status=ACTIVE" tone="text-emerald-600" />
        <StatCard title="Executions today" value={executions.data?.executionsToday} loading={executions.isPending} icon={<Activity className="size-4" />} href={`/executions?from=${today}`} />
        <StatCard title="Completed today" value={executions.data?.completedToday} loading={executions.isPending} icon={<CheckCircle2 className="size-4" />} href={`/executions?status=COMPLETED&from=${today}`} tone="text-emerald-600" />
        <StatCard title="Failed today" value={executions.data?.failedToday} loading={executions.isPending} icon={<XCircle className="size-4" />} href={`/executions?status=FAILED&from=${today}`} tone="text-red-600" />
        <StatCard title="Running" value={executions.data?.running} loading={executions.isPending} icon={<PlayCircle className="size-4" />} href="/executions?status=RUNNING" tone="text-blue-600" />
      </section>
      <RecentActivityCard executions={executions} />
    </PageContainer>
  );
}
