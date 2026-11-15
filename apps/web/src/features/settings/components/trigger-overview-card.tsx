"use client";
/** Lists active workflows started by webhooks or schedules, with a link to their trigger panel. */
import { CalendarClock, Webhook } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useWorkflowList } from "@/features/workflows/hooks/workflow-queries";

export function TriggerOverviewCard() {
  const list = useWorkflowList({ status: "ACTIVE", size: 100, sort: "name", direction: "asc" });
  const triggered = (list.data?.items ?? []).filter((w) => w.triggerType === "WEBHOOK_TRIGGER" || w.triggerType === "SCHEDULE_TRIGGER");
  return (
    <Card>
      <CardHeader>
        <CardTitle>Trigger configuration</CardTitle>
        <CardDescription>Active workflows started by webhooks or schedules. Open one to see its URL, secret rotation and deliveries.</CardDescription>
      </CardHeader>
      <CardContent>
        {list.isPending ? (
          <LoadingState />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => void list.refetch()} />
        ) : triggered.length === 0 ? (
          <EmptyState title="No webhook or schedule triggers" description="Publish a workflow with a webhook or schedule trigger to see it here." />
        ) : (
          <ul className="divide-y rounded-md border">
            {triggered.map((w) => (
              <li key={w.id} className="flex items-center justify-between gap-2 px-3 py-2">
                <span className="flex min-w-0 items-center gap-2">
                  {w.triggerType === "WEBHOOK_TRIGGER" ? <Webhook className="size-4 shrink-0" aria-label="Webhook" /> : <CalendarClock className="size-4 shrink-0" aria-label="Schedule" />}
                  <span className="truncate text-sm font-medium">{w.name}</span>
                  <span className="text-xs text-muted-foreground">v{w.latestVersion}</span>
                </span>
                <Button variant="outline" size="sm" asChild>
                  <Link href={`/workflows/${w.id}?panel=triggers`}>Triggers</Link>
                </Button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
