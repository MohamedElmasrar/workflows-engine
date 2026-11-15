"use client";
/** Webhook registration: URL with copy button, signature and field requirements, secret rotation. */
import { KeyRound, Webhook } from "lucide-react";
import { CopyButton } from "@/components/copy-button";
import { Button } from "@/components/ui/button";
import type { WebhookTrigger } from "@/lib/api/types";

export function WebhookSection({
  webhook,
  canRotate,
  rotating,
  onRotate,
}: {
  webhook: WebhookTrigger;
  canRotate: boolean;
  rotating: boolean;
  onRotate: () => void;
}) {
  return (
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
        <Button variant="outline" size="sm" onClick={onRotate} disabled={rotating}>
          <KeyRound /> Rotate signing secret
        </Button>
      ) : null}
    </section>
  );
}
