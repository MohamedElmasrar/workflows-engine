"use client";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { CorrelationId } from "@/components/states";

export function ConflictDialog({
  open,
  onOpenChange,
  onReload,
  onOverwrite,
  correlationId,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onReload: () => void;
  onOverwrite: () => void;
  correlationId: string | null;
}) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>This workflow was changed elsewhere</AlertDialogTitle>
          <AlertDialogDescription>
            Someone saved a newer revision while you were editing. Reload to get the latest version (your unsaved changes are
            discarded), or overwrite it with your version.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <CorrelationId id={correlationId} />
        <AlertDialogFooter>
          <AlertDialogCancel>Keep editing</AlertDialogCancel>
          <Button variant="outline" onClick={onOverwrite}>
            Overwrite with mine
          </Button>
          <AlertDialogAction onClick={onReload}>Reload latest</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
