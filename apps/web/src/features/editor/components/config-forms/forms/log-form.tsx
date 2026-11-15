"use client";
/** Log node config: message and level. */
import { LOG_LEVELS } from "@/schemas/node-configs";
import { logForm, logFormSchema } from "@/schemas/node-forms";
import { SelectField } from "../fields/select-field";
import { TemplateField } from "../fields/template-field";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

export function LogForm(props: TypeFormProps) {
  const form = useConfigForm(logFormSchema, logForm, props);
  return (
    <div className="space-y-4">
      <TemplateField form={form} name="message" nodeId={props.nodeId} label="Message" multiline placeholder="Order {{ trigger.body.orderId }} processed" disabled={props.disabled} />
      <SelectField
        control={form.control}
        name="level"
        label="Level"
        options={[{ value: "DEFAULT", label: "Default (INFO)" }, ...LOG_LEVELS.map((l) => ({ value: l, label: l }))]}
        disabled={props.disabled}
      />
    </div>
  );
}
