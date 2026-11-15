"use client";
/** Recent trigger deliveries (webhook calls, schedule firings) with links to the executions they started. */
import Link from "next/link";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { RelativeTime } from "@/components/relative-time";
import { Button } from "@/components/ui/button";
import { useDeliveries } from "@/features/workflows/hooks";
import { humanize } from "@/lib/format";

export function DeliveriesList({ workflowId, enabled }: { workflowId: string; enabled: boolean }) {
  const deliveries = useDeliveries(workflowId, enabled);
  return (
    <section className="space-y-2" aria-labelledby="deliveries-heading">
      <h3 id="deliveries-heading" className="text-sm font-semibold">
        Recent deliveries
      </h3>
      {deliveries.isPending ? (
        <LoadingState />
      ) : deliveries.isError ? (
        <ErrorState error={deliveries.error} />
      ) : deliveries.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">No deliveries yet.</p>
      ) : (
        <ul className="divide-y rounded-md border">
          {deliveries.data.map((d) => (
            <li key={d.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
              <div>
                <p>
                  {humanize(d.triggerType)} · {d.executionStatus ? humanize(d.executionStatus) : "—"}
                </p>
                <p className="text-xs text-muted-foreground">
                  <RelativeTime value={d.receivedAt} />
                  {d.payloadBytes !== null ? ` · ${d.payloadBytes} bytes` : ""}
                </p>
              </div>
              {d.executionId ? (
                <Button variant="link" size="sm" asChild>
                  <Link href={`/executions/${d.executionId}`}>View execution</Link>
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
