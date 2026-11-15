"use client";
/** Delay node config: duration in seconds. */
import { useWatch } from "react-hook-form";
import { formatSeconds } from "@/lib/format";
import { delayForm, delayFormSchema } from "@/schemas/node-forms";
import { InputField } from "../fields/input-field";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

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
