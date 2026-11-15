"use client";
/** Transform node config: source, pick, rename and set. */
import { transformForm, transformFormSchema } from "@/schemas/node-forms";
import { JsonField } from "../fields/json-field";
import { KeyValueField } from "../fields/key-value-field";
import { StringListField } from "../fields/string-list-field";
import { TemplateField } from "../fields/template-field";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

export function TransformForm(props: TypeFormProps) {
  const form = useConfigForm(transformFormSchema, transformForm, props);
  return (
    <div className="space-y-4">
      <TemplateField
        form={form}
        name="source"
        nodeId={props.nodeId}
        label="Source"
        optional
        multiline
        mono
        placeholder="{{ nodes.http_1.output.body }}"
        hint="Object to start from: a template or a JSON literal. pick/rename apply to it."
        disabled={props.disabled}
      />
      <StringListField form={form} name="pick" label="Pick fields" placeholder="orderId" addLabel="Add field" disabled={props.disabled} hint="Keep only these keys of the source." />
      <KeyValueField form={form} name="rename" label="Rename fields" keyPlaceholder="from" valuePlaceholder="to" addLabel="Add rename" disabled={props.disabled} />
      <JsonField form={form} name="set" label="Set fields" placeholder='{"total": "{{ trigger.body.amount }}"}' hint="JSON object merged into the result; values may be templates." disabled={props.disabled} />
    </div>
  );
}
