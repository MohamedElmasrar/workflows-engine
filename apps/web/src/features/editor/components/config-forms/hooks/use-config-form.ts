/** Shared wiring of a node config form: react-hook-form + zod, initial values from the stored config, live sync back. */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { type DefaultValues, type FieldValues, useForm } from "react-hook-form";
import type { z } from "zod";
import type { TypeFormProps } from "../forms/type-form-props";
import { useLiveSync } from "./use-live-sync";

export function useConfigForm<F extends FieldValues>(
  schema: z.ZodType<F, F>,
  converters: { toForm: (c: Record<string, unknown>) => F; fromForm: (f: F, prev: Record<string, unknown>) => Record<string, unknown> },
  { config, onChange }: TypeFormProps,
) {
  // Initial values only: the form owns the field state while the node is selected
  // (the panel is keyed by node id, so a different node remounts the form).
  const [defaultValues] = useState(() => converters.toForm(config) as DefaultValues<F>);
  const form = useForm<F>({ resolver: zodResolver(schema), defaultValues, mode: "onChange" });
  useLiveSync(form, (values) => onChange(converters.fromForm(values, config)));
  return form;
}
