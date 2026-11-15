"use client";
/** Editable key/value rows (headers, query parameters, renames) with an optional extra control per row. */
import { Plus, Trash2 } from "lucide-react";
import type { ReactNode } from "react";
import { type ArrayPath, type FieldValues, type Path, useFieldArray, type UseFormReturn } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorAt } from "./error-at";

export function KeyValueField<T extends FieldValues>({
  form,
  name,
  label,
  keyPlaceholder = "Key",
  valuePlaceholder = "Value",
  addLabel,
  disabled,
  hint,
  extra,
  newRow,
}: {
  form: UseFormReturn<T>;
  name: ArrayPath<T>;
  label: string;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
  addLabel: string;
  disabled?: boolean;
  hint?: ReactNode;
  extra?: (index: number) => ReactNode;
  newRow?: Record<string, string>;
}) {
  const { fields, append, remove } = useFieldArray({ control: form.control, name });
  const rootError = errorAt(form.formState.errors, name);
  return (
    <fieldset className="space-y-1.5">
      <legend className="text-xs font-medium">
        {label} <span className="font-normal text-muted-foreground">(optional)</span>
      </legend>
      {fields.map((field, index) => {
        const keyPath = `${name}.${index}.key` as Path<T>;
        const valuePath = `${name}.${index}.value` as Path<T>;
        const keyError = errorAt(form.formState.errors, keyPath);
        const valueError = errorAt(form.formState.errors, valuePath);
        return (
          <div key={field.id} className="space-y-0.5">
            <div className="flex items-center gap-1">
              <Input
                {...form.register(keyPath)}
                aria-label={`${label} key ${index + 1}`}
                aria-invalid={keyError ? true : undefined}
                placeholder={keyPlaceholder}
                disabled={disabled}
                className="w-2/5 font-mono text-xs"
              />
              <Input
                {...form.register(valuePath)}
                aria-label={`${label} value ${index + 1}`}
                aria-invalid={valueError ? true : undefined}
                placeholder={valuePlaceholder}
                disabled={disabled}
                className="flex-1 font-mono text-xs"
              />
              {extra?.(index)}
              <Button type="button" variant="ghost" size="icon-sm" onClick={() => remove(index)} aria-label={`Remove ${label} ${index + 1}`} disabled={disabled}>
                <Trash2 />
              </Button>
            </div>
            {keyError || valueError ? <p className="text-xs text-destructive">{keyError ?? valueError}</p> : null}
          </div>
        );
      })}
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={disabled}
        onClick={() => append((newRow ?? { key: "", value: "" }) as Parameters<typeof append>[0])}
      >
        <Plus /> {addLabel}
      </Button>
      {rootError ? <p className="text-xs text-destructive">{rootError}</p> : null}
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </fieldset>
  );
}
