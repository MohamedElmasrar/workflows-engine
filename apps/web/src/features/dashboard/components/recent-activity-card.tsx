/** Dashboard list of the most recent executions, with loading and empty states. */
import { Clock } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/empty-state";
import { RelativeTime } from "@/components/relative-time";
import { ExecutionStatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useExecutionStats } from "@/features/executions/hooks/execution-queries";
import { formatDuration, humanize } from "@/lib/format";

export function RecentActivityCard({ executions }: { executions: ReturnType<typeof useExecutionStats> }) {
  return (
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
  );
}
