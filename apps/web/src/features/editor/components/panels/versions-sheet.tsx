"use client";

import { Eye, History, RotateCcw } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { RelativeTime } from "@/components/relative-time";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { ExecutionGraph } from "@/features/executions/components/execution-graph";
import { useWorkflowVersion, useWorkflowVersions } from "@/features/workflows/hooks/workflow-queries";
import { useEditorStore } from "@/stores/editor/editor-store";

export function VersionsSheet({ workflowId, open, onOpenChange }: { workflowId: string; open: boolean; onOpenChange: (open: boolean) => void }) {
  const versions = useWorkflowVersions(workflowId, open);
  const readOnly = useEditorStore((s) => s.readOnly);
  const loadDefinition = useEditorStore((s) => s.loadDefinition);
  const [viewing, setViewing] = useState<number | null>(null);
  const [restoring, setRestoring] = useState<number | null>(null);
  const version = useWorkflowVersion(workflowId, viewing ?? restoring);

  const restore = () => {
    const definition = version.data?.definition;
    if (!definition) return;
    loadDefinition(definition);
    toast.success(`Version ${version.data?.version} loaded into the draft`, { description: "Save to keep it." });
    setRestoring(null);
    setViewing(null);
    onOpenChange(false);
  };

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="w-full sm:max-w-md">
          <SheetHeader>
            <SheetTitle>Versions</SheetTitle>
            <SheetDescription>Published versions are immutable. Executions always run a specific version.</SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-auto px-4 pb-4">
            {versions.isPending ? (
              <LoadingState />
            ) : versions.isError ? (
              <ErrorState error={versions.error} onRetry={() => void versions.refetch()} />
            ) : versions.data.length === 0 ? (
              <EmptyState icon={<History className="size-5" />} title="Not published yet" description="Publish the workflow to create version 1." />
            ) : (
              <ul className="space-y-2">
                {versions.data.map((v) => (
                  <li key={v.version} className="flex items-center justify-between gap-2 rounded-md border p-3">
                    <div>
                      <p className="text-sm font-medium">
                        v{v.version} · {v.name}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        Published <RelativeTime value={v.publishedAt} />
                      </p>
                    </div>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="sm" onClick={() => setViewing(v.version)} aria-label={`View version ${v.version}`}>
                        <Eye /> View
                      </Button>
                      {!readOnly ? (
                        <Button variant="ghost" size="sm" onClick={() => setRestoring(v.version)} aria-label={`Restore version ${v.version} into draft`}>
                          <RotateCcw /> Restore
                        </Button>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </SheetContent>
      </Sheet>
      <Dialog open={viewing !== null} onOpenChange={(o) => !o && setViewing(null)}>
        <DialogContent className="flex h-[80vh] flex-col sm:max-w-5xl">
          <DialogHeader>
            <DialogTitle>Version {viewing}</DialogTitle>
            <DialogDescription>Read-only view of the published definition.</DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 rounded-md border">
            {version.isPending ? (
              <LoadingState />
            ) : version.isError ? (
              <ErrorState error={version.error} />
            ) : version.data.definition ? (
              <ExecutionGraph definition={version.data.definition} />
            ) : null}
          </div>
          {!readOnly ? (
            <div className="flex justify-end">
              <Button onClick={() => setRestoring(viewing)} disabled={!version.data?.definition}>
                <RotateCcw /> Restore into draft
              </Button>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={restoring !== null}
        onOpenChange={(o) => !o && setRestoring(null)}
        title={`Restore version ${restoring}?`}
        description="The current draft graph and settings are replaced by this version as unsaved changes. Nothing is saved until you click Save."
        confirmLabel={version.isPending ? "Loading…" : "Restore"}
        pending={version.isPending}
        onConfirm={restore}
      />
    </>
  );
}
