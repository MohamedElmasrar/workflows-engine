"use client";
/** Workflow name and description, shown when nothing is selected. */
import { zodResolver } from "@hookform/resolvers/zod";
import { useId, useState } from "react";
import { useForm } from "react-hook-form";
import { Textarea } from "@/components/ui/textarea";
import { workflowSettingsFormSchema, type WorkflowSettingsForm } from "@/schemas/node-forms";
import { useEditorStore } from "@/stores/editor/editor-store";
import { errorAt } from "@/features/editor/components/config-forms/fields/error-at";
import { Field, fieldAria } from "@/features/editor/components/config-forms/fields/field";
import { InputField } from "@/features/editor/components/config-forms/fields/input-field";
import { useLiveSync } from "@/features/editor/components/config-forms/hooks/use-live-sync";

export function WorkflowSettingsPanel({ readOnly }: { readOnly: boolean }) {
  const meta = useEditorStore((s) => s.meta);
  const setMeta = useEditorStore((s) => s.setMeta);
  const nodeCount = useEditorStore((s) => s.nodes.length);
  const edgeCount = useEditorStore((s) => s.edges.length);
  const [defaultValues] = useState<WorkflowSettingsForm>(() => ({
    name: meta.name,
    description: meta.description,
    executionTimeoutSeconds: meta.settings?.executionTimeoutSeconds,
    maxConcurrentExecutions: meta.settings?.maxConcurrentExecutions,
  }));
  const form = useForm<WorkflowSettingsForm>({ resolver: zodResolver(workflowSettingsFormSchema), defaultValues, mode: "onChange" });
  useLiveSync(form, (values) => {
    const settings: NonNullable<typeof meta.settings> = {};
    if (values.executionTimeoutSeconds !== undefined) settings.executionTimeoutSeconds = values.executionTimeoutSeconds;
    if (values.maxConcurrentExecutions !== undefined) settings.maxConcurrentExecutions = values.maxConcurrentExecutions;
    setMeta({ name: values.name.trim(), description: values.description, settings: Object.keys(settings).length ? settings : undefined });
  });
  const descriptionId = useId();
  const descriptionError = errorAt(form.formState.errors, "description");
  return (
    <div className="space-y-5" data-testid="workflow-settings-panel">
      <div>
        <h2 className="text-sm font-semibold">Workflow settings</h2>
        <p className="text-xs text-muted-foreground">
          {nodeCount} nodes · {edgeCount} connections. Select a node to configure it.
        </p>
      </div>
      <fieldset disabled={readOnly} className="space-y-4">
        <InputField form={form} name="name" label="Name" disabled={readOnly} />
        <Field id={descriptionId} label="Description" optional error={descriptionError}>
          <Textarea {...form.register("description")} {...fieldAria(descriptionId, descriptionError)} rows={3} disabled={readOnly} />
        </Field>
        <InputField
          form={form}
          name="executionTimeoutSeconds"
          label="Execution timeout (seconds)"
          type="number"
          numeric
          optional
          placeholder="3600"
          hint="1–86400. Running executions past this deadline are TIMED_OUT."
          disabled={readOnly}
        />
        <InputField
          form={form}
          name="maxConcurrentExecutions"
          label="Max concurrent executions"
          type="number"
          numeric
          optional
          placeholder="10"
          hint="1–50. Excess executions wait in PENDING."
          disabled={readOnly}
        />
      </fieldset>
    </div>
  );
}
