"use client";
/** Labelled select bound to a react-hook-form field. */
import { useId } from "react";
import { type Control, Controller, type FieldValues, type Path } from "react-hook-form";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Field } from "./field";

export function SelectField<T extends FieldValues>({
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
