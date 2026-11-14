import { z } from "zod";
import type { RetryPolicy } from "@/lib/api/types";
import {
  COMBINATORS,
  CONDITION_OPERATORS,
  HTTP_METHODS,
  isFiveFieldCron,
  LOG_LEVELS,
  NOTIFICATION_CHANNELS,
} from "./node-configs";

/**
 * Form-level schemas (what the inputs hold) and converters to/from the stored config.
 * Converters omit empty optional fields so the saved config stays minimal and schema-valid.
 */

// ---------------------------------------------------------------------------------------------
// JSON literal helpers: values that may be "any JSON" are typed as a JSON literal or plain text.

/** Parses user text: valid JSON becomes its value, anything else (e.g. `{{ trigger.x }}`) stays a string. */
export function parseLiteral(text: string): unknown {
  if (text.trim() === "") return undefined;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

/** Inverse of parseLiteral: strings that would parse as JSON are quoted to keep their type. */
export function formatLiteral(value: unknown): string {
  if (value === undefined) return "";
  if (typeof value === "string") {
    try {
      JSON.parse(value);
      return JSON.stringify(value);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, typeof value === "object" && value !== null ? 2 : undefined);
}

export function formatJson(value: unknown): string {
  return value === undefined ? "" : JSON.stringify(value, null, 2);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Optional textarea that must contain a JSON object when non-empty. */
export const jsonObjectText = z.string().refine((text) => {
  if (text.trim() === "") return true;
  try {
    return isPlainObject(JSON.parse(text));
  } catch {
    return false;
  }
}, "Must be a JSON object, e.g. {\"key\": \"value\"}");

/** Optional textarea with any JSON value. */
export const jsonAnyText = z.string().refine((text) => {
  if (text.trim() === "") return true;
  try {
    JSON.parse(text);
    return true;
  } catch {
    return false;
  }
}, "Must be valid JSON (use quotes for strings)");

function parseJsonObject(text: string): Record<string, unknown> | undefined {
  if (text.trim() === "") return undefined;
  const parsed: unknown = JSON.parse(text);
  return isPlainObject(parsed) ? parsed : undefined;
}

const stringItem = z.object({ value: z.string() });
type StringItem = z.infer<typeof stringItem>;
const toItems = (values: unknown): StringItem[] => (Array.isArray(values) ? values.map((v) => ({ value: String(v) })) : []);
const fromItems = (items: StringItem[]): string[] => items.map((i) => i.value.trim()).filter((v) => v !== "");

const keyValue = z.object({ key: z.string(), value: z.string() });
type KeyValue = z.infer<typeof keyValue>;

function uniqueKeys(rows: { key: string }[], ctx: z.RefinementCtx, field: string) {
  const seen = new Set<string>();
  rows.forEach((row, index) => {
    const key = row.key.trim();
    if (key === "") {
      ctx.addIssue({ code: "custom", message: "Key is required", path: [index, "key"] });
    } else if (seen.has(key)) {
      ctx.addIssue({ code: "custom", message: `Duplicate ${field}`, path: [index, "key"] });
    }
    seen.add(key);
  });
}

function recordFromRows<R extends { key: string }, T>(rows: R[], value: (row: R) => T): Record<string, T> | undefined {
  if (rows.length === 0) return undefined;
  const out: Record<string, T> = {};
  for (const row of rows) out[row.key.trim()] = value(row);
  return out;
}

// ---------------------------------------------------------------------------------------------
// Node basics: name, timeout, retry policy

const optionalInt = (min: number, max: number, label: string) =>
  z
    .number({ error: `${label} must be a number` })
    .int(`${label} must be a whole number`)
    .min(min, `${label} must be at least ${min}`)
    .max(max, `${label} must be at most ${max}`)
    .optional();

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

// ---------------------------------------------------------------------------------------------
// Per-type config forms

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

export const QUERY_VALUE_KINDS = ["string", "number", "boolean"] as const;
const queryRow = z.object({ key: z.string(), value: z.string(), kind: z.enum(QUERY_VALUE_KINDS) });
type QueryRow = z.infer<typeof queryRow>;

export const httpFormSchema = z.object({
  method: z.enum(HTTP_METHODS),
  url: z.string().trim().min(1, "URL is required").max(2048),
  headers: z
    .array(keyValue.extend({ value: z.string().max(4096, "At most 4096 characters") }))
    .max(50, "At most 50 headers")
    .superRefine((rows, ctx) => uniqueKeys(rows, ctx, "header")),
  query: z
    .array(queryRow)
    .max(50, "At most 50 query parameters")
    .superRefine((rows, ctx) => {
      uniqueKeys(rows, ctx, "parameter");
      rows.forEach((row, index) => {
        if (row.kind === "number" && (row.value.trim() === "" || Number.isNaN(Number(row.value)))) {
          ctx.addIssue({ code: "custom", message: "Not a number", path: [index, "value"] });
        }
        if (row.kind === "boolean" && row.value !== "true" && row.value !== "false") {
          ctx.addIssue({ code: "custom", message: "Use true or false", path: [index, "value"] });
        }
      });
    }),
  body: jsonAnyText,
  failOnHttpError: z.boolean(),
});
export type HttpForm = z.infer<typeof httpFormSchema>;
export const httpForm = {
  toForm: (c: Record<string, unknown>): HttpForm => ({
    method: HTTP_METHODS.includes(c.method as (typeof HTTP_METHODS)[number]) ? (c.method as HttpForm["method"]) : "GET",
    url: typeof c.url === "string" ? c.url : "",
    headers: isPlainObject(c.headers) ? Object.entries(c.headers).map(([key, value]) => ({ key, value: String(value) })) : [],
    query: isPlainObject(c.query)
      ? Object.entries(c.query).map(([key, value]) => ({
          key,
          value: String(value),
          kind: typeof value === "number" ? "number" : typeof value === "boolean" ? "boolean" : "string",
        }))
      : [],
    body: c.body === undefined ? "" : formatJson(c.body),
    failOnHttpError: c.failOnHttpError !== false,
  }),
  fromForm: (f: HttpForm, prev: Record<string, unknown>): Record<string, unknown> => {
    const out: Record<string, unknown> = { method: f.method, url: f.url.trim() };
    const headers = recordFromRows(f.headers, (row) => row.value);
    if (headers) out.headers = headers;
    const query = recordFromRows(f.query, (row: QueryRow): string | number | boolean =>
      row.kind === "number" ? Number(row.value) : row.kind === "boolean" ? row.value === "true" : row.value,
    );
    if (query) out.query = query;
    if (f.body.trim() !== "") out.body = JSON.parse(f.body) as unknown;
    if (!f.failOnHttpError) out.failOnHttpError = false;
    else if (prev.failOnHttpError === true) out.failOnHttpError = true;
    return out;
  },
};

export const transformFormSchema = z.object({
  source: z.string(),
  pick: z.array(stringItem).max(100, "At most 100 fields"),
  rename: z
    .array(keyValue)
    .superRefine((rows, ctx) => {
      uniqueKeys(rows, ctx, "field");
      rows.forEach((row, index) => {
        if (row.value.trim() === "") ctx.addIssue({ code: "custom", message: "New name is required", path: [index, "value"] });
      });
    }),
  set: jsonObjectText,
});
export type TransformForm = z.infer<typeof transformFormSchema>;
export const transformForm = {
  toForm: (c: Record<string, unknown>): TransformForm => ({
    source: formatLiteral(c.source),
    pick: toItems(c.pick),
    rename: isPlainObject(c.rename) ? Object.entries(c.rename).map(([key, value]) => ({ key, value: String(value) })) : [],
    set: c.set === undefined ? "" : formatJson(c.set),
  }),
  fromForm: (f: TransformForm): Record<string, unknown> => {
    const out: Record<string, unknown> = {};
    const source = parseLiteral(f.source);
    if (source !== undefined) out.source = source;
    const pick = fromItems(f.pick);
    if (pick.length) out.pick = pick;
    const rename = recordFromRows(f.rename, (row: KeyValue) => row.value.trim());
    if (rename) out.rename = rename;
    const setValue = parseJsonObject(f.set);
    if (setValue) out.set = setValue;
    return out;
  },
};

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

const clause = z
  .object({ left: z.string(), operator: z.enum(CONDITION_OPERATORS), right: z.string() })
  .superRefine((value, ctx) => {
    if (value.left.trim() === "") ctx.addIssue({ code: "custom", message: "Left operand is required", path: ["left"] });
    if (value.operator !== "EXISTS" && value.right.trim() === "") {
      ctx.addIssue({ code: "custom", message: "Right operand is required", path: ["right"] });
    }
  });
export const conditionFormSchema = z.object({
  combinator: z.enum(COMBINATORS),
  conditions: z.array(clause).min(1, "Add at least one condition").max(20, "At most 20 conditions"),
});
export type ConditionForm = z.infer<typeof conditionFormSchema>;
export const conditionForm = {
  toForm: (c: Record<string, unknown>): ConditionForm => ({
    combinator: c.combinator === "OR" ? "OR" : "AND",
    conditions: Array.isArray(c.conditions)
      ? c.conditions.filter(isPlainObject).map((cond) => ({
          left: formatLiteral(cond.left),
          operator: CONDITION_OPERATORS.includes(cond.operator as (typeof CONDITION_OPERATORS)[number])
            ? (cond.operator as ConditionForm["conditions"][number]["operator"])
            : "EQUALS",
          right: formatLiteral(cond.right),
        }))
      : [],
  }),
  fromForm: (f: ConditionForm, prev: Record<string, unknown>): Record<string, unknown> => {
    const out: Record<string, unknown> = {};
    if (f.combinator === "OR" || prev.combinator !== undefined) out.combinator = f.combinator;
    out.conditions = f.conditions.map((cond) => {
      const item: Record<string, unknown> = { left: parseLiteral(cond.left), operator: cond.operator };
      if (cond.operator !== "EXISTS") {
        const right = parseLiteral(cond.right);
        if (right !== undefined) item.right = right;
      }
      return item;
    });
    return out;
  },
};

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

// ---------------------------------------------------------------------------------------------
// Workflow settings (panel shown when nothing is selected)

export const workflowSettingsFormSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(100, "At most 100 characters"),
  description: z.string().max(1000, "At most 1000 characters"),
  executionTimeoutSeconds: optionalInt(1, 86400, "Execution timeout"),
  maxConcurrentExecutions: optionalInt(1, 50, "Max concurrent executions"),
});
export type WorkflowSettingsForm = z.infer<typeof workflowSettingsFormSchema>;

export const createWorkflowSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(100, "At most 100 characters"),
  description: z.string().max(1000, "At most 1000 characters"),
});
export type CreateWorkflowForm = z.infer<typeof createWorkflowSchema>;

/** RHF `setValueAs` for optional numeric inputs: empty → undefined. */
export function optionalNumber(value: unknown): number | undefined {
  if (value === "" || value === null || value === undefined) return undefined;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isNaN(n) ? Number.NaN : n;
}
