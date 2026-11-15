/** Config forms of the Log and Notification nodes. */
import { z } from "zod";
import { LOG_LEVELS, NOTIFICATION_CHANNELS } from "../node-configs";

const DEFAULT = "DEFAULT" as const;
export const logFormSchema = z.object({
  message: z.string().min(1, "Message is required").max(4000),
  level: z.enum([DEFAULT, ...LOG_LEVELS]),
});
export type LogForm = z.infer<typeof logFormSchema>;
export const logForm = {
  toForm: (c: Record<string, unknown>): LogForm => ({
    message: typeof c.message === "string" ? c.message : "",
    level: LOG_LEVELS.includes(c.level as (typeof LOG_LEVELS)[number]) ? (c.level as LogForm["level"]) : DEFAULT,
  }),
  fromForm: (f: LogForm): Record<string, unknown> => {
    const out: Record<string, unknown> = { message: f.message };
    if (f.level !== DEFAULT) out.level = f.level;
    return out;
  },
};

export const notificationFormSchema = z.object({
  channel: z.enum(NOTIFICATION_CHANNELS),
  recipient: z.string().trim().min(1, "Recipient is required").max(320),
  subject: z.string().max(200, "At most 200 characters"),
  message: z.string().min(1, "Message is required").max(10000),
});
export type NotificationForm = z.infer<typeof notificationFormSchema>;
export const notificationForm = {
  toForm: (c: Record<string, unknown>): NotificationForm => ({
    channel: c.channel === "EMAIL" ? "EMAIL" : "LOG",
    recipient: typeof c.recipient === "string" ? c.recipient : "",
    subject: typeof c.subject === "string" ? c.subject : "",
    message: typeof c.message === "string" ? c.message : "",
  }),
  fromForm: (f: NotificationForm): Record<string, unknown> => {
    const out: Record<string, unknown> = { channel: f.channel, recipient: f.recipient.trim() };
    if (f.subject.trim() !== "") out.subject = f.subject;
    out.message = f.message;
    return out;
  },
};
