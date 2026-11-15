"use client";
/** Editable list of string rows (e.g. required webhook fields, transform pick). */
import { Plus, Trash2 } from "lucide-react";
import { type ReactNode, useId } from "react";
import { type ArrayPath, type FieldValues, type Path, useFieldArray, type UseFormReturn } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorAt } from "./error-at";

export function StringListField<T extends FieldValues>({
  form,
  name,
  label,
  hint,
  placeholder,
  addLabel,
  disabled,
}: {
  form: UseFormReturn<T>;
  name: ArrayPath<T>;
  label: string;
  hint?: ReactNode;
  placeholder?: string;
  addLabel: string;
  disabled?: boolean;
}) {
  const { fields, append, remove } = useFieldArray({ control: form.control, name });
  const groupId = useId();
  const rootError = errorAt(form.formState.errors, name);
  return (
    <fieldset className="space-y-1.5" aria-describedby={hint ? `${groupId}-hint` : undefined}>
      <legend className="text-xs font-medium">
        {label} <span className="font-normal text-muted-foreground">(optional)</span>
      </legend>
      {fields.map((field, index) => {
        const path = `${name}.${index}.value` as Path<T>;
        const error = errorAt(form.formState.errors, path);
        return (
          <div key={field.id} className="flex items-start gap-1">
            <div className="flex-1">
              <Input
                {...form.register(path)}
                aria-label={`${label} ${index + 1}`}
                aria-invalid={error ? true : undefined}
                placeholder={placeholder}
                disabled={disabled}
              />
              {error ? <p className="text-xs text-destructive">{error}</p> : null}
            </div>
            <Button type="button" variant="ghost" size="icon-sm" onClick={() => remove(index)} aria-label={`Remove ${label} ${index + 1}`} disabled={disabled}>
              <Trash2 />
            </Button>
          </div>
        );
      })}
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={disabled}
        onClick={() => append({ value: "" } as Parameters<typeof append>[0])}
      >
        <Plus /> {addLabel}
      </Button>
      {rootError ? <p className="text-xs text-destructive">{rootError}</p> : null}
      {hint ? (
        <p id={`${groupId}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </fieldset>
  );
}
