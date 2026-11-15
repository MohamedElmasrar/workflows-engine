/** Config forms of the Webhook and Schedule triggers. */
import { z } from "zod";
import { isFiveFieldCron } from "../node-configs";
import { fromItems, stringItem, toItems } from "./form-helpers";
import { formatJson, jsonObjectText, parseJsonObject } from "./literals";

export const webhookFormSchema = z.object({
  requireSignature: z.boolean(),
  requiredFields: z.array(stringItem).max(50, "At most 50 fields"),
});
export type WebhookForm = z.infer<typeof webhookFormSchema>;
export const webhookForm = {
  toForm: (c: Record<string, unknown>): WebhookForm => ({
    requireSignature: c.requireSignature === true,
    requiredFields: toItems(c.requiredFields),
  }),
  fromForm: (f: WebhookForm, prev: Record<string, unknown>): Record<string, unknown> => {
    const out: Record<string, unknown> = {};
    if (f.requireSignature) out.requireSignature = true;
    else if (prev.requireSignature === false) out.requireSignature = false;
    const fields = fromItems(f.requiredFields);
    if (fields.length) out.requiredFields = fields;
    return out;
  },
};

export const scheduleFormSchema = z.object({
  cron: z
    .string()
    .trim()
    .min(9, "Cron expression is too short")
    .max(120)
    .refine(isFiveFieldCron, "Use five fields: minute hour day-of-month month day-of-week"),
  timezone: z.string().min(1, "Timezone is required").max(64),
  payload: jsonObjectText,
});
export type ScheduleForm = z.infer<typeof scheduleFormSchema>;
export const scheduleForm = {
  toForm: (c: Record<string, unknown>): ScheduleForm => ({
    cron: typeof c.cron === "string" ? c.cron : "",
    timezone: typeof c.timezone === "string" ? c.timezone : "UTC",
    payload: c.payload === undefined ? "" : formatJson(c.payload),
  }),
  fromForm: (f: ScheduleForm): Record<string, unknown> => {
    const out: Record<string, unknown> = { cron: f.cron.trim(), timezone: f.timezone };
    const payload = parseJsonObject(f.payload);
    if (payload) out.payload = payload;
    return out;
  },
};
