"use client";
/** Plain registered text or number input. */
import { type ReactNode, useId } from "react";
import type { FieldValues, Path, UseFormReturn } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { errorAt } from "./error-at";
import { Field, fieldAria } from "./field";

export function InputField<T extends FieldValues>({
  form,
  name,
  label,
  hint,
  placeholder,
  type = "text",
  optional,
  disabled,
  numeric,
  className,
  step,
}: {
  form: UseFormReturn<T>;
  name: Path<T>;
  label: ReactNode;
  hint?: ReactNode;
  placeholder?: string;
  type?: "text" | "number";
  optional?: boolean;
  disabled?: boolean;
  numeric?: boolean;
  className?: string;
  step?: string;
}) {
  const id = useId();
  const error = errorAt(form.formState.errors, name);
  return (
    <Field id={id} label={label} error={error} hint={hint} optional={optional} className={className}>
      <Input
        {...form.register(name, numeric ? { setValueAs: numberOrUndefined } : undefined)}
        {...fieldAria(id, error, Boolean(hint))}
        type={type}
        inputMode={numeric ? "decimal" : undefined}
        step={step}
        placeholder={placeholder}
        disabled={disabled}
      />
    </Field>
  );
}

function numberOrUndefined(value: unknown): number | undefined {
  if (value === "" || value === null || value === undefined) return undefined;
  if (typeof value === "number") return value;
  const n = Number(value);
  return Number.isNaN(n) ? Number.NaN : n;
}
