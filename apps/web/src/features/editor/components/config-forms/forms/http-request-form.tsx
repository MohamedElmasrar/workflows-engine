"use client";
/** HTTP Request node config: method, URL, headers, typed query parameters, body, error handling. */
import { type Control, Controller } from "react-hook-form";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { HTTP_METHODS } from "@/schemas/node-configs";
import { httpForm, type HttpForm, httpFormSchema, QUERY_VALUE_KINDS } from "@/schemas/node-forms";
import { JsonField } from "../fields/json-field";
import { KeyValueField } from "../fields/key-value-field";
import { SelectField } from "../fields/select-field";
import { SwitchField } from "../fields/switch-field";
import { TemplateField } from "../fields/template-field";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

export function HttpRequestForm(props: TypeFormProps) {
  const form = useConfigForm(httpFormSchema, httpForm, props);
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-[7rem_1fr] gap-2">
        <SelectField
          control={form.control}
          name="method"
          label="Method"
          options={HTTP_METHODS.map((m) => ({ value: m, label: m }))}
          disabled={props.disabled}
        />
        <TemplateField form={form} name="url" nodeId={props.nodeId} label="URL" placeholder="https://api.example.com/items" disabled={props.disabled} mono />
      </div>
      <KeyValueField form={form} name="headers" label="Headers" keyPlaceholder="Header" addLabel="Add header" disabled={props.disabled}
        hint="Header values are stored in the workflow definition in plain text." />
      <KeyValueField
        form={form}
        name="query"
        label="Query parameters"
        keyPlaceholder="name"
        addLabel="Add parameter"
        disabled={props.disabled}
        newRow={{ key: "", value: "", kind: "string" }}
        extra={(index) => <QueryKindSelect control={form.control} index={index} disabled={props.disabled} />}
      />
      <JsonField form={form} name="body" label="Body (JSON)" placeholder='{"orderId": "{{ trigger.body.orderId }}"}' hint="Any JSON value; strings may contain templates." disabled={props.disabled} />
      <SwitchField
        control={form.control}
        name="failOnHttpError"
        label="Fail on HTTP error"
        description="Treat responses with status ≥ 400 as failures (5xx and 429 are retried)."
        disabled={props.disabled}
      />
    </div>
  );
}

function QueryKindSelect({ control, index, disabled }: { control: Control<HttpForm>; index: number; disabled?: boolean }) {
  return (
    <Controller
      control={control}
      name={`query.${index}.kind`}
      render={({ field }) => (
        <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
          <SelectTrigger size="sm" className="w-24" aria-label={`Query parameter type ${index + 1}`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {QUERY_VALUE_KINDS.map((k) => (
              <SelectItem key={k} value={k}>
                {k}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
    />
  );
}
