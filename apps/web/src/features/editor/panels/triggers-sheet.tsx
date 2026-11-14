"use client";

import { CalendarClock, KeyRound, Webhook } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { CopyButton } from "@/components/copy-button";
import { RelativeTime } from "@/components/relative-time";
import { describeError, EmptyState, ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import type { WorkflowRole } from "@/lib/api/types";
import { formatAbsolute, humanize } from "@/lib/format";
import { useDeliveries, useRotateSecret, useTriggers } from "@/features/workflows/hooks";

function signatureExample(url: string, secret: string): string {
  return [
    `SECRET='${secret}'`,
    `BODY='{"orderId":"A-1001","amount":250}'`,
    "TS=$(date +%s)",
    `SIG="sha256=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $NF}')"`,
    `curl -X POST '${url}' \\`,
    "  -H 'Content-Type: application/json' \\",
    '  -H "X-Webhook-Timestamp: $TS" \\',
    '  -H "X-Webhook-Signature: $SIG" \\',
    '  -H "X-Webhook-Delivery-Id: $(uuidgen)" \\',
    '  -d "$BODY"',
  ].join("\n");
}

export function TriggersSheet({
  workflowId,
  role,
  open,
  onOpenChange,
}: {
  workflowId: string;
  role: WorkflowRole;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const triggers = useTriggers(workflowId, open);
  const deliveries = useDeliveries(workflowId, open);
  const rotate = useRotateSecret(workflowId);
  const [secret, setSecret] = useState<string | null>(null);
  const canRotate = role === "OWNER" || role === "EDITOR";

  const onRotate = async () => {
    try {
      const result = await rotate.mutateAsync();
      setSecret(result.secret);
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    }
  };

  const webhook = triggers.data?.webhook ?? null;
  const schedule = triggers.data?.schedule ?? null;
  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="w-full sm:max-w-lg">
          <SheetHeader>
            <SheetTitle>Triggers</SheetTitle>
            <SheetDescription>Registrations of the published version. Changes take effect when you publish.</SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-6 overflow-auto px-4 pb-6">
            {triggers.isPending ? (
              <LoadingState />
            ) : triggers.isError ? (
              <ErrorState error={triggers.error} onRetry={() => void triggers.refetch()} />
            ) : !webhook && !schedule ? (
              <EmptyState
                title="No webhook or schedule registered"
                description="Manual workflows have no trigger registration. For webhook or schedule triggers, publish the workflow; registration can take a few seconds."
              />
            ) : null}
            {webhook ? (
              <section className="space-y-3 rounded-lg border p-4" aria-labelledby="webhook-heading">
                <h3 id="webhook-heading" className="flex items-center gap-2 text-sm font-semibold">
                  <Webhook className="size-4" aria-hidden /> Webhook {webhook.active ? "" : "(inactive)"}
                </h3>
                {webhook.url ? (
                  <div className="flex items-center gap-1 rounded-md bg-muted p-2">
                    <code className="flex-1 truncate text-xs" title={webhook.url} data-testid="webhook-url">
                      {webhook.url}
                    </code>
                    <CopyButton value={webhook.url} label="Copy webhook URL" />
                  </div>
                ) : (
                  <p className="text-xs text-muted-foreground">The URL is only shown to owners and editors.</p>
                )}
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  <dt className="text-muted-foreground">Signature</dt>
                  <dd>{webhook.requireSignature ? "Required (HMAC-SHA256)" : "Not required"}</dd>
                  <dt className="text-muted-foreground">Required fields</dt>
                  <dd>{webhook.requiredFields.length ? webhook.requiredFields.join(", ") : "—"}</dd>
                  <dt className="text-muted-foreground">Version</dt>
                  <dd>v{webhook.version}</dd>
                </dl>
                {canRotate ? (
                  <Button variant="outline" size="sm" onClick={onRotate} disabled={rotate.isPending}>
                    <KeyRound /> Rotate signing secret
                  </Button>
                ) : null}
              </section>
            ) : null}
            {schedule ? (
              <section className="space-y-3 rounded-lg border p-4" aria-labelledby="schedule-heading">
                <h3 id="schedule-heading" className="flex items-center gap-2 text-sm font-semibold">
                  <CalendarClock className="size-4" aria-hidden /> Schedule {schedule.active ? "" : "(inactive)"}
                </h3>
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  <dt className="text-muted-foreground">Cron</dt>
                  <dd className="font-mono">{schedule.cron}</dd>
                  <dt className="text-muted-foreground">Timezone</dt>
                  <dd>{schedule.timezone}</dd>
                  <dt className="text-muted-foreground">Next fire</dt>
                  <dd title={formatAbsolute(schedule.nextFireAt)}>
                    {schedule.nextFireAt ? formatAbsolute(schedule.nextFireAt) : "—"}
                  </dd>
                  <dt className="text-muted-foreground">Last fired</dt>
                  <dd>
                    <RelativeTime value={schedule.lastFiredAt} />
                  </dd>
                </dl>
              </section>
            ) : null}
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
          </div>
        </SheetContent>
      </Sheet>
      <Dialog open={secret !== null} onOpenChange={(o) => !o && setSecret(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>New signing secret</DialogTitle>
            <DialogDescription>
              Copy it now: it is shown only once and cannot be retrieved later. The previous secret no longer works.
            </DialogDescription>
          </DialogHeader>
          {secret ? (
            <div className="space-y-4">
              <div className="flex items-center gap-1 rounded-md border bg-muted p-2">
                <code className="flex-1 break-all text-xs" data-testid="webhook-secret">
                  {secret}
                </code>
                <CopyButton value={secret} label="Copy secret" />
              </div>
              <div className="space-y-1">
                <p className="text-sm font-medium">Example signed request</p>
                <div className="relative">
                  <pre className="max-h-64 overflow-auto rounded-md border bg-muted/50 p-3 text-xs">{signatureExample(webhook?.url ?? "<webhook-url>", secret)}</pre>
                  <div className="absolute top-1 right-1">
                    <CopyButton value={signatureExample(webhook?.url ?? "<webhook-url>", secret)} label="Copy example" size="icon-xs" />
                  </div>
                </div>
                <p className="text-xs text-muted-foreground">
                  Signature: <code>sha256=hex(HMAC-SHA256(secret, timestamp + &quot;.&quot; + rawBody))</code>; the timestamp must be within ±5 minutes.
                </p>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
