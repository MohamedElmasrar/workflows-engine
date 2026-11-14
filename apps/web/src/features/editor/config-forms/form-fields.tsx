"use client";

import { Braces, Plus, Trash2 } from "lucide-react";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import {
  useFieldArray,
  type ArrayPath,
  type FieldError,
  type FieldErrors,
  type FieldValues,
  type Path,
  type PathValue,
  type UseFormReturn,
} from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useTemplateReferences } from "../template-references";

/** Reads a nested error by dotted path (RHF's `get` is not exported in a typed way). */
export function errorAt(errors: FieldErrors, path: string): string | undefined {
  let current: unknown = errors;
  for (const part of path.split(".")) {
    if (typeof current !== "object" || current === null) return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  if (typeof current === "object" && current !== null) {
    const err = current as Partial<FieldError> & { root?: FieldError };
    if (typeof err.message === "string") return err.message;
    if (err.root && typeof err.root.message === "string") return err.root.message;
  }
  return undefined;
}

export function Field({
  id,
  label,
  error,
  hint,
  children,
  className,
  optional,
}: {
  id: string;
  label: ReactNode;
  error?: string;
  hint?: ReactNode;
  children: ReactNode;
  className?: string;
  optional?: boolean;
}) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <Label htmlFor={id} className="text-xs font-medium">
        {label}
        {optional ? <span className="font-normal text-muted-foreground">(optional)</span> : null}
      </Label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-xs text-destructive" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export function fieldAria(id: string, error?: string, hint?: boolean) {
  return {
    id,
    "aria-invalid": error ? true : undefined,
    "aria-describedby": error ? `${id}-error` : hint ? `${id}-hint` : undefined,
  } as const;
}

/** Popover listing template references (`{{ ... }}`) available to the current node. */
export function ReferencePicker({ nodeId, onInsert, disabled }: { nodeId: string; onInsert: (text: string) => void; disabled?: boolean }) {
  const references = useTemplateReferences(nodeId);
  const [open, setOpen] = useState(false);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="icon-xs" aria-label="Insert reference" title="Insert reference" disabled={disabled}>
          <Braces />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-2">
        <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">Insert reference</p>
        <ul className="max-h-72 overflow-auto" role="listbox" aria-label="Template references">
          {references.map((ref) => (
            <li key={ref.value}>
              <button
                type="button"
                role="option"
                aria-selected={false}
                className="flex w-full flex-col items-start rounded-md px-2 py-1.5 text-left hover:bg-muted focus-visible:bg-muted focus-visible:outline-none"
                onClick={() => {
                  onInsert(ref.value);
                  setOpen(false);
                }}
              >
                <code className="text-xs">{ref.value}</code>
                <span className="text-xs text-muted-foreground">{ref.label}</span>
              </button>
            </li>
          ))}
        </ul>
      </PopoverContent>
    </Popover>
  );
}

type TextControlProps<T extends FieldValues> = {
  form: UseFormReturn<T>;
  name: Path<T>;
  nodeId: string;
  label: ReactNode;
  hint?: ReactNode;
  placeholder?: string;
  multiline?: boolean;
  optional?: boolean;
  disabled?: boolean;
  mono?: boolean;
  className?: string;
};

/** Text input / textarea with an "insert reference" helper that inserts at the caret. */
export function TemplateField<T extends FieldValues>({
  form,
  name,
  nodeId,
  label,
  hint,
  placeholder,
  multiline,
  optional,
  disabled,
  mono,
  className,
}: TextControlProps<T>) {
  const id = useId();
  const error = errorAt(form.formState.errors, name);
  const elementRef = useRef<HTMLInputElement | HTMLTextAreaElement | null>(null);
  const { ref, ...registration } = form.register(name);
  const insert = (text: string) => {
    const el = elementRef.current;
    const current = String(form.getValues(name) ?? "");
    const start = el?.selectionStart ?? current.length;
    const end = el?.selectionEnd ?? current.length;
    const next = current.slice(0, start) + text + current.slice(end);
    form.setValue(name, next as PathValue<T, Path<T>>, { shouldDirty: true, shouldTouch: true });
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(start + text.length, start + text.length);
    });
  };
  const setRefs = (el: HTMLInputElement | HTMLTextAreaElement | null) => {
    elementRef.current = el;
    ref(el);
  };
  return (
    <Field
      id={id}
      label={
        <span className="flex w-full items-center justify-between gap-2">
          <span className="flex items-center gap-1">
            {label}
            {optional ? <span className="font-normal text-muted-foreground">(optional)</span> : null}
          </span>
        </span>
      }
      error={error}
      hint={hint}
      className={className}
    >
      <div className="relative">
        {multiline ? (
          <Textarea
            {...registration}
            ref={setRefs}
            {...fieldAria(id, error, Boolean(hint))}
            placeholder={placeholder}
            disabled={disabled}
            rows={3}
            className={cn("pr-8", mono && "font-mono text-xs")}
          />
        ) : (
          <Input
            {...registration}
            ref={setRefs}
            {...fieldAria(id, error, Boolean(hint))}
            placeholder={placeholder}
            disabled={disabled}
            className={cn("pr-8", mono && "font-mono text-xs")}
          />
        )}
        <div className="absolute top-1 right-1">
          <ReferencePicker nodeId={nodeId} onInsert={insert} disabled={disabled} />
        </div>
      </div>
    </Field>
  );
}

/** Plain registered input (number or text). */
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

/** JSON textarea (validated by the form schema). */
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

/** Editable list of `{ value }` rows (e.g. required fields, pick). */
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

/** Key/value rows (`{ key, value }`), optionally with an extra per-row control. */
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

/**
 * Pushes valid form values to `onValid` whenever the user edits the form (live sync to the
 * editor store). Invalid states are shown inline and not written.
 */
export function useLiveSync<T extends FieldValues>(form: UseFormReturn<T>, onValid: (values: T) => void): void {
  const callback = useRef(onValid);
  useEffect(() => {
    callback.current = onValid;
  }, [onValid]);
  useEffect(() => {
    const subscription = form.watch((_values, info) => {
      if (!info.type && !info.name) return;
      void form.trigger().then((valid) => {
        if (valid) callback.current(form.getValues());
      });
    });
    return () => subscription.unsubscribe();
  }, [form]);
}
