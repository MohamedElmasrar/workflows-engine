"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Info, Plus, Trash2 } from "lucide-react";
import { useId, useState } from "react";
import { Controller, useFieldArray, useForm, useWatch, type Control, type DefaultValues, type FieldValues, type Path } from "react-hook-form";
import type { z } from "zod";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import type { NodeType } from "@/lib/api/types";
import { formatSeconds } from "@/lib/format";
import { COMBINATORS, CONDITION_OPERATORS, HTTP_METHODS, LOG_LEVELS, NOTIFICATION_CHANNELS } from "@/schemas/node-configs";
import {
  conditionForm,
  conditionFormSchema,
  delayForm,
  delayFormSchema,
  httpForm,
  httpFormSchema,
  logForm,
  logFormSchema,
  notificationForm,
  notificationFormSchema,
  QUERY_VALUE_KINDS,
  scheduleForm,
  scheduleFormSchema,
  transformForm,
  transformFormSchema,
  webhookForm,
  webhookFormSchema,
  type ConditionForm,
  type HttpForm,
} from "@/schemas/node-forms";
import { TimezoneSelect } from "./timezone-select";
import { errorAt, Field, InputField, JsonField, KeyValueField, StringListField, TemplateField, useLiveSync } from "./form-fields";

export interface TypeFormProps {
  nodeId: string;
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
  disabled?: boolean;
}

/** Shared wiring: RHF + zod resolver, initial values from the stored config, live sync back. */
function useConfigForm<F extends FieldValues>(
  schema: z.ZodType<F, F>,
  converters: { toForm: (c: Record<string, unknown>) => F; fromForm: (f: F, prev: Record<string, unknown>) => Record<string, unknown> },
  { config, onChange }: TypeFormProps,
) {
  // Initial values only: the form owns the field state while the node is selected
  // (the panel is keyed by node id, so a different node remounts the form).
  const [defaultValues] = useState(() => converters.toForm(config) as DefaultValues<F>);
  const form = useForm<F>({ resolver: zodResolver(schema), defaultValues, mode: "onChange" });
  useLiveSync(form, (values) => onChange(converters.fromForm(values, config)));
  return form;
}

function SelectField<T extends FieldValues>({
  control,
  name,
  label,
  options,
  disabled,
  className,
}: {
  control: Control<T>;
  name: Path<T>;
  label: string;
  options: readonly { value: string; label: string }[];
  disabled?: boolean;
  className?: string;
}) {
  const id = useId();
  return (
    <Field id={id} label={label} className={className}>
      <Controller
        control={control}
        name={name}
        render={({ field }) => (
          <Select value={String(field.value)} onValueChange={field.onChange} disabled={disabled}>
            <SelectTrigger id={id} className="w-full" onBlur={field.onBlur}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {options.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      />
    </Field>
  );
}

function SwitchField<T extends FieldValues>({
  control,
  name,
  label,
  description,
  disabled,
}: {
  control: Control<T>;
  name: Path<T>;
  label: string;
  description?: string;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <div className="flex items-start justify-between gap-3 rounded-md border p-3">
          <div className="space-y-0.5">
            <label htmlFor={id} className="text-xs font-medium">
              {label}
            </label>
            {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
          </div>
          <Switch id={id} checked={Boolean(field.value)} onCheckedChange={field.onChange} disabled={disabled} />
        </div>
      )}
    />
  );
}

export function ManualTriggerForm() {
  return (
    <p className="flex gap-2 rounded-md bg-muted/50 p-3 text-xs text-muted-foreground">
      <Info className="size-4 shrink-0" aria-hidden />
      Manual triggers have no configuration. The JSON input you provide in the Run dialog becomes the trigger output,
      available as <code>{"{{ trigger.<field> }}"}</code>.
    </p>
  );
}

export function WebhookTriggerForm(props: TypeFormProps) {
  const form = useConfigForm(webhookFormSchema, webhookForm, props);
  return (
    <div className="space-y-4">
      <SwitchField
        control={form.control}
        name="requireSignature"
        label="Require HMAC signature"
        description="Reject deliveries without a valid X-Webhook-Signature header."
        disabled={props.disabled}
      />
      <StringListField
        form={form}
        name="requiredFields"
        label="Required body fields"
        placeholder="orderId"
        addLabel="Add field"
        hint="Deliveries missing any of these top-level fields are rejected with 422."
        disabled={props.disabled}
      />
      <p className="text-xs text-muted-foreground">
        The URL and signing secret are available in the Triggers panel once the workflow is published.
      </p>
    </div>
  );
}

export function ScheduleTriggerForm(props: TypeFormProps) {
  const form = useConfigForm(scheduleFormSchema, scheduleForm, props);
  const id = useId();
  return (
    <div className="space-y-4">
      <InputField
        form={form}
        name="cron"
        label="Cron expression"
        placeholder="0 9 * * 1-5"
        hint="Five fields: minute hour day-of-month month day-of-week."
        disabled={props.disabled}
      />
      <Field id={id} label="Timezone" error={errorAt(form.formState.errors, "timezone")}>
        <Controller
          control={form.control}
          name="timezone"
          render={({ field }) => <TimezoneSelect id={id} value={field.value} onChange={field.onChange} disabled={props.disabled} />}
        />
      </Field>
      <JsonField form={form} name="payload" label="Payload" placeholder='{"report": "daily"}' hint="JSON object passed as trigger.payload." disabled={props.disabled} />
    </div>
  );
}

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

export function NotificationForm(props: TypeFormProps) {
  const form = useConfigForm(notificationFormSchema, notificationForm, props);
  return (
    <div className="space-y-4">
      <SelectField
        control={form.control}
        name="channel"
        label="Channel"
        options={NOTIFICATION_CHANNELS.map((c) => ({ value: c, label: c === "EMAIL" ? "E-mail" : "Log" }))}
        disabled={props.disabled}
      />
      <TemplateField form={form} name="recipient" nodeId={props.nodeId} label="Recipient" placeholder="team@example.com" disabled={props.disabled} />
      <TemplateField form={form} name="subject" nodeId={props.nodeId} label="Subject" optional placeholder="Order received" disabled={props.disabled} />
      <TemplateField form={form} name="message" nodeId={props.nodeId} label="Message" multiline disabled={props.disabled} />
    </div>
  );
}

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

export function DelayNodeForm(props: TypeFormProps) {
  const form = useConfigForm(delayFormSchema, delayForm, props);
  const seconds = useWatch({ control: form.control, name: "durationSeconds" });
  return (
    <div className="space-y-4">
      <InputField
        form={form}
        name="durationSeconds"
        label="Duration (seconds)"
        type="number"
        numeric
        hint={typeof seconds === "number" && seconds > 0 ? `= ${formatSeconds(seconds)} (max 7 days)` : "1 – 604800 seconds"}
        disabled={props.disabled}
      />
      <p className="text-xs text-muted-foreground">Delays are durable timers: they survive engine restarts.</p>
    </div>
  );
}

export function TypeConfigForm({ type, ...props }: TypeFormProps & { type: NodeType }) {
  switch (type) {
    case "MANUAL_TRIGGER":
      return <ManualTriggerForm />;
    case "WEBHOOK_TRIGGER":
      return <WebhookTriggerForm {...props} />;
    case "SCHEDULE_TRIGGER":
      return <ScheduleTriggerForm {...props} />;
    case "HTTP_REQUEST":
      return <HttpRequestForm {...props} />;
    case "TRANSFORM":
      return <TransformForm {...props} />;
    case "LOG":
      return <LogForm {...props} />;
    case "NOTIFICATION":
      return <NotificationForm {...props} />;
    case "CONDITION":
      return <ConditionNodeForm {...props} />;
    case "DELAY":
      return <DelayNodeForm {...props} />;
  }
}

