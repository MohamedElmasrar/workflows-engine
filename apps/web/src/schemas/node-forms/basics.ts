/** Fields every node has: name, timeout and retry policy. */
import { z } from "zod";
import type { RetryPolicy } from "@/lib/api/types";
import { optionalInt } from "./form-helpers";

export const nodeBasicsFormSchema = z
  .object({
    name: z.string().trim().min(1, "Name is required").max(100, "At most 100 characters"),
    timeoutSeconds: optionalInt(1, 300, "Timeout"),
    retryEnabled: z.boolean(),
    maxAttempts: optionalInt(1, 10, "Max attempts"),
    initialIntervalMs: optionalInt(100, 3_600_000, "Initial interval"),
    multiplier: z
      .number({ error: "Multiplier must be a number" })
      .min(1, "Multiplier must be at least 1")
      .max(10, "Multiplier must be at most 10")
      .optional(),
    maxIntervalMs: optionalInt(100, 3_600_000, "Max interval"),
  })
  .superRefine((value, ctx) => {
    if (value.retryEnabled && value.maxAttempts === undefined) {
      ctx.addIssue({ code: "custom", message: "Max attempts is required", path: ["maxAttempts"] });
    }
  });
export type NodeBasicsForm = z.infer<typeof nodeBasicsFormSchema>;

export function basicsToForm(data: { name: string; timeoutSeconds?: number; retryPolicy?: RetryPolicy }): NodeBasicsForm {
  return {
    name: data.name,
    timeoutSeconds: data.timeoutSeconds,
    retryEnabled: data.retryPolicy !== undefined,
    maxAttempts: data.retryPolicy?.maxAttempts,
    initialIntervalMs: data.retryPolicy?.initialIntervalMs,
    multiplier: data.retryPolicy?.multiplier,
    maxIntervalMs: data.retryPolicy?.maxIntervalMs,
  };
}

export function basicsFromForm(form: NodeBasicsForm): { name: string; timeoutSeconds: number | undefined; retryPolicy: RetryPolicy | undefined } {
  let retryPolicy: RetryPolicy | undefined;
  if (form.retryEnabled && form.maxAttempts !== undefined) {
    retryPolicy = { maxAttempts: form.maxAttempts };
    if (form.initialIntervalMs !== undefined) retryPolicy.initialIntervalMs = form.initialIntervalMs;
    if (form.multiplier !== undefined) retryPolicy.multiplier = form.multiplier;
    if (form.maxIntervalMs !== undefined) retryPolicy.maxIntervalMs = form.maxIntervalMs;
  }
  return { name: form.name.trim(), timeoutSeconds: form.timeoutSeconds, retryPolicy };
}
