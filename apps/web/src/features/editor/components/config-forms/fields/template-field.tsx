"use client";
/** Text input or textarea with an "insert reference" button that inserts a template at the caret. */
import { type ReactNode, useId, useRef } from "react";
import type { FieldValues, Path, PathValue, UseFormReturn } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { errorAt } from "./error-at";
import { Field, fieldAria } from "./field";
import { ReferencePicker } from "./reference-picker";

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
