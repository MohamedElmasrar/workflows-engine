/**
 * Save, validate and publish for the editor, including their error handling: revision conflicts open the
 * conflict dialog, 422 responses show the validation panel, everything else becomes a toast.
 */
import { useCallback, useState } from "react";
import { toast } from "sonner";
import { describeError } from "@/components/describe-error";
import { isApiError } from "@/lib/api/api-error";
import type { ValidationIssue } from "@/lib/api/types";
import { useEditorStore } from "@/stores/editor/editor-store";
import { usePublishWorkflow, useSaveWorkflow, useValidateWorkflow } from "@/features/workflows/hooks/workflow-mutations";
import { useWorkflow } from "@/features/workflows/hooks/workflow-queries";

export interface ValidationView {
  valid: boolean;
  issues: ValidationIssue[];
}

export interface ConflictView {
  correlationId: string | null;
  currentRevision: number | null;
}

export function useEditorActions(workflowId: string) {
  const query = useWorkflow(workflowId);
  const save = useSaveWorkflow(workflowId);
  const validate = useValidateWorkflow(workflowId);
  const publish = usePublishWorkflow(workflowId);
  const setIssues = useEditorStore((s) => s.setIssues);
  const markSaved = useEditorStore((s) => s.markSaved);
  const load = useEditorStore((s) => s.load);
  const [validation, setValidation] = useState<ValidationView | null>(null);
  const [conflict, setConflict] = useState<ConflictView | null>(null);

  const showIssues = useCallback(
    (issues: ValidationIssue[]) => {
      setIssues(issues);
      setValidation({ valid: false, issues });
    },
    [setIssues],
  );

  const doSave = useCallback(
    async (overrideRevision?: number): Promise<boolean> => {
      const state = useEditorStore.getState();
      if (state.readOnly) return false;
      const counter = state.changeCounter;
      try {
        const saved = await save.mutateAsync({
          name: state.meta.name,
          description: state.meta.description.trim() ? state.meta.description : null,
          definition: state.toDefinition(),
          revision: overrideRevision ?? state.revision,
        });
        markSaved(saved.revision, counter);
        toast.success("Workflow saved", { description: `Revision ${saved.revision}` });
        return true;
      } catch (error) {
        if (isApiError(error) && error.status === 409) {
          const current = error.extensions.currentRevision;
          setConflict({ correlationId: error.correlationId, currentRevision: typeof current === "number" ? current : null });
        } else if (isApiError(error) && error.status === 422) {
          showIssues(error.errors ?? []);
          toast.error("The workflow could not be saved", { description: error.detail ?? "Fix the highlighted problems." });
        } else {
          const info = describeError(error);
          toast.error(info.title, { description: `${info.detail}${info.correlationId ? ` (correlation ${info.correlationId})` : ""}` });
        }
        return false;
      }
    },
    [save, markSaved, showIssues],
  );

  const doValidate = async () => {
    try {
      const result = await validate.mutateAsync(useEditorStore.getState().toDefinition());
      setIssues(result.errors);
      setValidation({ valid: result.valid, issues: result.errors });
      if (result.valid) toast.success("No problems found");
    } catch (error) {
      if (isApiError(error) && error.status === 422 && error.errors) {
        showIssues(error.errors);
        return;
      }
      const info = describeError(error);
      toast.error(info.title, { description: info.detail });
    }
  };

  const doPublish = async () => {
    if (useEditorStore.getState().dirty && !(await doSave())) return;
    try {
      const version = await publish.mutateAsync();
      setIssues([]);
      setValidation(null);
      toast.success(`Published version ${version.version}`, { description: "New executions use this version." });
    } catch (error) {
      if (isApiError(error) && error.status === 422) {
        showIssues(error.errors ?? []);
        toast.error("Publishing failed", { description: error.detail ?? "Fix the problems first." });
        return;
      }
      const info = describeError(error);
      toast.error(info.title, { description: info.detail });
    }
  };

  const reloadLatest = async () => {
    setConflict(null);
    const result = await query.refetch();
    if (result.data) {
      load(result.data);
      toast.info("Loaded the latest revision");
    }
  };

  /** "Overwrite with mine" in the conflict dialog: save again on top of the server's current revision. */
  const overwrite = () => {
    const revision = conflict?.currentRevision;
    setConflict(null);
    if (typeof revision === "number") void doSave(revision);
    else void reloadLatest();
  };

  return {
    save: doSave,
    validate: doValidate,
    publish: doPublish,
    reloadLatest,
    overwrite,
    saving: save.isPending,
    validating: validate.isPending,
    publishing: publish.isPending,
    validation,
    closeValidation: () => setValidation(null),
    conflict,
    closeConflict: () => setConflict(null),
  };
}
