"use client";

import { Activity, CheckCircle2, Clock, PlayCircle, Waypoints, XCircle, Zap } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { RelativeTime } from "@/components/relative-time";
import { EmptyState, ErrorState } from "@/components/states";
import { ExecutionStatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDuration, humanize } from "@/lib/format";
import { useAuth } from "@/lib/auth/auth-provider";
import { useExecutionStats } from "@/features/executions/hooks";
import { useWorkflowStats } from "@/features/workflows/hooks";

function StatCard({ title, value, icon, loading, href, tone }: {
  title: string;
  value: number | undefined;
  icon: ReactNode;
  loading: boolean;
  href?: string;
  tone?: string;
}) {
  const body = (
    <Card className="h-full transition-colors hover:border-primary/40">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
        <span className={tone ?? "text-muted-foreground"} aria-hidden>
          {icon}
        </span>
      </CardHeader>
      <CardContent>
        {loading ? <Skeleton className="h-8 w-16" /> : <p className="text-3xl font-semibold tabular-nums" data-testid={`stat-${title}`}>{value ?? "—"}</p>}
      </CardContent>
    </Card>
  );
  return href ? (
    <Link href={href} className="rounded-xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none" aria-label={`${title}: ${value ?? ""}`}>
      {body}
    </Link>
  ) : (
    body
  );
}

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
      <Card className="mt-6">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base">Recent activity</CardTitle>
          <Button variant="ghost" size="sm" asChild>
            <Link href="/executions">View all</Link>
          </Button>
        </CardHeader>
        <CardContent>
          {executions.isPending ? (
            <div className="space-y-3" role="status" aria-label="Loading recent activity">
              {Array.from({ length: 5 }, (_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : executions.isError ? null : executions.data.recent.length === 0 ? (
            <EmptyState
              icon={<Clock className="size-5" />}
              title="No executions yet"
              description="Publish a workflow and run it to see activity here."
              action={
                <Button asChild variant="outline">
                  <Link href="/workflows">Go to workflows</Link>
                </Button>
              }
            />
          ) : (
            <ul className="divide-y" data-testid="recent-activity">
              {executions.data.recent.map((e) => (
                <li key={e.id}>
                  <Link
                    href={`/executions/${e.id}`}
                    className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md px-2 py-3 hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                  >
                    <ExecutionStatusBadge status={e.status} />
                    <span className="min-w-0 flex-1 truncate font-medium">
                      {e.workflowName} <span className="text-xs font-normal text-muted-foreground">v{e.workflowVersion}</span>
                    </span>
                    <span className="text-sm text-muted-foreground">{humanize(e.triggerType)}</span>
                    <span className="hidden w-20 text-right text-sm text-muted-foreground sm:inline">{formatDuration(e.durationMs)}</span>
                    <RelativeTime value={e.createdAt} className="w-28 text-right text-sm text-muted-foreground" />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </PageContainer>
  );
}
