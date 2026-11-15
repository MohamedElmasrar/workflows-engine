"use client";
/** Condition node config: AND/OR over a list of comparisons that route to the true or false branch. */
import { Plus, Trash2 } from "lucide-react";
import { useFieldArray, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { COMBINATORS, CONDITION_OPERATORS } from "@/schemas/node-configs";
import { conditionForm, type ConditionForm, conditionFormSchema } from "@/schemas/node-forms";
import { errorAt } from "../fields/error-at";
import { SelectField } from "../fields/select-field";
import { TemplateField } from "../fields/template-field";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

const OPERATOR_LABELS: Record<(typeof CONDITION_OPERATORS)[number], string> = {
  EQUALS: "equals",
  NOT_EQUALS: "does not equal",
  GREATER_THAN: "is greater than",
  LESS_THAN: "is less than",
  CONTAINS: "contains",
  EXISTS: "exists",
};

export function ConditionNodeForm(props: TypeFormProps) {
  const form = useConfigForm(conditionFormSchema, conditionForm, props);
  const { fields, append, remove } = useFieldArray({ control: form.control, name: "conditions" });
  const values = useWatch({ control: form.control, name: "conditions" });
  const rootError = errorAt(form.formState.errors, "conditions");
  return (
    <div className="space-y-4">
      <SelectField
        control={form.control}
        name="combinator"
        label="Match"
        options={COMBINATORS.map((c) => ({ value: c, label: c === "AND" ? "All conditions (AND)" : "Any condition (OR)" }))}
        disabled={props.disabled}
      />
      <div className="space-y-3">
        {fields.map((field, index) => {
          const operator = values?.[index]?.operator;
          return (
            <fieldset key={field.id} className="space-y-2 rounded-md border p-3">
              <legend className="px-1 text-xs font-medium">Condition {index + 1}</legend>
              <TemplateField form={form} name={`conditions.${index}.left`} nodeId={props.nodeId} label="Left" mono placeholder="{{ nodes.http_1.output.status }}" disabled={props.disabled} />
              <SelectField<ConditionForm>
                control={form.control}
                name={`conditions.${index}.operator`}
                label="Operator"
                options={CONDITION_OPERATORS.map((o) => ({ value: o, label: OPERATOR_LABELS[o] }))}
                disabled={props.disabled}
              />
              {operator !== "EXISTS" ? (
                <TemplateField form={form} name={`conditions.${index}.right`} nodeId={props.nodeId} label="Right" mono placeholder="200" disabled={props.disabled} />
              ) : null}
              <div className="flex justify-end">
                <Button type="button" variant="ghost" size="sm" onClick={() => remove(index)} disabled={props.disabled || fields.length <= 1}>
                  <Trash2 /> Remove
                </Button>
              </div>
            </fieldset>
          );
        })}
      </div>
      {rootError ? <p className="text-xs text-destructive">{rootError}</p> : null}
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => append({ left: "", operator: "EQUALS", right: "" })}
        disabled={props.disabled || fields.length >= 20}
      >
        <Plus /> Add condition
      </Button>
      <p className="text-xs text-muted-foreground">
        Values are JSON literals (<code>70</code>, <code>true</code>, <code>&quot;text&quot;</code>) or plain text / templates. The
        node routes to the <strong>true</strong> or <strong>false</strong> handle.
      </p>
    </div>
  );
}
