"use client";
/** Shows a freshly rotated signing secret exactly once, with a signed-request example. */
import { CopyButton } from "@/components/copy-button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { signatureExample } from "./signature-example";

export function NewSecretDialog({
  secret,
  webhookUrl,
  onClose,
}: {
  secret: string | null;
  webhookUrl: string | null;
  onClose: () => void;
}) {
  const example = signatureExample(webhookUrl ?? "<webhook-url>", secret ?? "");
  return (
    <Dialog open={secret !== null} onOpenChange={(o) => !o && onClose()}>
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
                <pre className="max-h-64 overflow-auto rounded-md border bg-muted/50 p-3 text-xs">{example}</pre>
                <div className="absolute top-1 right-1">
                  <CopyButton value={example} label="Copy example" size="icon-xs" />
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
  );
}
