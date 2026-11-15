"use client";
/** JSON textarea (the form schema validates the content). */
import { type ReactNode, useId } from "react";
import type { FieldValues, Path, UseFormReturn } from "react-hook-form";
import { Textarea } from "@/components/ui/textarea";
import { errorAt } from "./error-at";
import { Field, fieldAria } from "./field";

export function JsonField<T extends FieldValues>({
  form,
  name,
  label,
  hint,
  placeholder,
  disabled,
}: {
  form: UseFormReturn<T>;
  name: Path<T>;
  label: ReactNode;
  hint?: ReactNode;
  placeholder?: string;
  disabled?: boolean;
}) {
  const id = useId();
  const error = errorAt(form.formState.errors, name);
  return (
    <Field id={id} label={label} error={error} hint={hint} optional>
      <Textarea
        {...form.register(name)}
        {...fieldAria(id, error, Boolean(hint))}
        placeholder={placeholder}
        disabled={disabled}
        rows={5}
        spellCheck={false}
        className="font-mono text-xs"
      />
    </Field>
  );
}
