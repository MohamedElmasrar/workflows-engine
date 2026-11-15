/** Config form of the Delay node. */
import { z } from "zod";

export const delayFormSchema = z.object({
  durationSeconds: z
    .number({ error: "Duration must be a number" })
    .int("Whole seconds only")
    .min(1, "At least 1 second")
    .max(604800, "At most 7 days (604800 s)"),
});
export type DelayForm = z.infer<typeof delayFormSchema>;
export const delayForm = {
  toForm: (c: Record<string, unknown>): DelayForm => ({
    durationSeconds: typeof c.durationSeconds === "number" ? c.durationSeconds : 60,
  }),
  fromForm: (f: DelayForm): Record<string, unknown> => ({ durationSeconds: f.durationSeconds }),
};
