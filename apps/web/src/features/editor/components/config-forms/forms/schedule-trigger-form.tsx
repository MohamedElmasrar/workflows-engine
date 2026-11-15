"use client";
/** Schedule trigger config: cron expression, time zone and payload. */
import { useId } from "react";
import { Controller } from "react-hook-form";
import { scheduleForm, scheduleFormSchema } from "@/schemas/node-forms";
import { errorAt } from "../fields/error-at";
import { Field } from "../fields/field";
import { InputField } from "../fields/input-field";
import { JsonField } from "../fields/json-field";
import { TimezoneSelect } from "../fields/timezone-select";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

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
