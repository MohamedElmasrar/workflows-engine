/** Pushes valid form values to the editor store whenever the user edits; invalid states only show inline errors. */
import { useEffect, useRef } from "react";
import type { FieldValues, UseFormReturn } from "react-hook-form";

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
