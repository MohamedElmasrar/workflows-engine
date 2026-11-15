"use client";
/** Triggers sheet of the editor: webhook and schedule registrations of the published version and recent deliveries. */
import { useState } from "react";
import { toast } from "sonner";
import { describeError } from "@/components/describe-error";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { useRotateSecret } from "@/features/workflows/hooks/workflow-mutations";
import { useTriggers } from "@/features/workflows/hooks/workflow-queries";
import type { WorkflowRole } from "@/lib/api/types";
import { DeliveriesList } from "./triggers/deliveries-list";
import { NewSecretDialog } from "./triggers/new-secret-dialog";
import { ScheduleSection } from "./triggers/schedule-section";
import { WebhookSection } from "./triggers/webhook-section";

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
            {webhook ? <WebhookSection webhook={webhook} canRotate={canRotate} rotating={rotate.isPending} onRotate={() => void onRotate()} /> : null}
            {schedule ? <ScheduleSection schedule={schedule} /> : null}
            <DeliveriesList workflowId={workflowId} enabled={open} />
          </div>
        </SheetContent>
      </Sheet>
      <NewSecretDialog secret={secret} webhookUrl={webhook?.url ?? null} onClose={() => setSecret(null)} />
    </>
  );
}
