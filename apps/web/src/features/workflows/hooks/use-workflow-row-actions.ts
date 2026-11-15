/** Duplicate and archive actions for workflow rows, with toasts; archive waits for the confirm dialog. */
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { describeError } from "@/components/describe-error";
import type { WorkflowSummary } from "@/lib/api/types";
import { useArchiveWorkflow, useDuplicateWorkflow } from "./workflow-mutations";

export function useWorkflowRowActions() {
  const router = useRouter();
  const duplicate = useDuplicateWorkflow();
  const archive = useArchiveWorkflow();
  const [archiving, setArchiving] = useState<WorkflowSummary | null>(null);

  const onDuplicate = async (workflow: WorkflowSummary) => {
    try {
      const copy = await duplicate.mutateAsync(workflow.id);
      toast.success(`Duplicated as “${copy.name}”`, {
        action: { label: "Open", onClick: () => router.push(`/workflows/${copy.id}`) },
      });
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    }
  };

  const onArchive = async () => {
    if (!archiving) return;
    try {
      await archive.mutateAsync(archiving.id);
      toast.success(`Archived “${archiving.name}”`);
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    } finally {
      setArchiving(null);
    }
  };

  return { archive, archiving, setArchiving, onDuplicate, onArchive };
}
