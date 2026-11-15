"use client";
/** Labelled on/off switch with a description, bound to a react-hook-form field. */
import { useId } from "react";
import { type Control, Controller, type FieldValues, type Path } from "react-hook-form";
import { Switch } from "@/components/ui/switch";

export function SwitchField<T extends FieldValues>({
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
