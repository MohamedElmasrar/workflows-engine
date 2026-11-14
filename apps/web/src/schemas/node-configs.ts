import { z } from "zod";
import type { NodeType } from "@/lib/api/types";

/**
 * Zod mirrors of packages/contracts/src/main/resources/schemas/workflow-definition.v1.json.
 * Every object is strict (`additionalProperties: false` in the JSON schema).
 */

export const IDENTIFIER_PATTERN = /^[A-Za-z][A-Za-z0-9_-]{0,63}$/;
export const identifierSchema = z.string().regex(IDENTIFIER_PATTERN, "Must start with a letter; letters, digits, _ and - only (max 64)");

const jsonObject = z.record(z.string(), z.unknown());

/** Five whitespace-separated cron fields (minute hour day-of-month month day-of-week). */
export function isFiveFieldCron(value: string): boolean {
  const fields = value.trim().split(/\s+/);
  return fields.length === 5 && fields.every((f) => /^[\dA-Za-z*,\-/?#LW]+$/.test(f));
}

const requiredUnknown = (label: string) => z.unknown().refine((v) => v !== undefined, `${label} is required`);

export const manualTriggerConfigSchema = z.strictObject({});

export const webhookTriggerConfigSchema = z.strictObject({
  requireSignature: z.boolean().optional(),
  requiredFields: z.array(z.string().min(1).max(200)).max(50).optional(),
});

export const scheduleTriggerConfigSchema = z.strictObject({
  cron: z.string().min(9, "Cron expression is too short").max(120).refine(isFiveFieldCron, "Use five cron fields, e.g. `0 9 * * 1-5`"),
  timezone: z.string().min(1, "Timezone is required").max(64),
  payload: jsonObject.optional(),
});

export const HTTP_METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE"] as const;

export const httpRequestConfigSchema = z.strictObject({
  method: z.enum(HTTP_METHODS),
  url: z.string().min(1, "URL is required").max(2048),
  headers: z
    .record(z.string(), z.string().max(4096))
    .refine((h) => Object.keys(h).length <= 50, "At most 50 headers")
    .optional(),
  query: z
    .record(z.string(), z.union([z.string(), z.number(), z.boolean()]))
    .refine((q) => Object.keys(q).length <= 50, "At most 50 query parameters")
    .optional(),
  body: z.unknown().optional(),
  failOnHttpError: z.boolean().optional(),
});

export const transformConfigSchema = z.strictObject({
  source: z.unknown().optional(),
  pick: z.array(z.string().min(1)).max(100).optional(),
  rename: z.record(z.string(), z.string().min(1)).optional(),
  set: jsonObject.optional(),
});

export const LOG_LEVELS = ["INFO", "WARN", "ERROR"] as const;

export const logConfigSchema = z.strictObject({
  message: z.string().min(1, "Message is required").max(4000),
  level: z.enum(LOG_LEVELS).optional(),
});

export const NOTIFICATION_CHANNELS = ["EMAIL", "LOG"] as const;

export const notificationConfigSchema = z.strictObject({
  channel: z.enum(NOTIFICATION_CHANNELS),
  recipient: z.string().min(1, "Recipient is required").max(320),
  subject: z.string().max(200).optional(),
  message: z.string().min(1, "Message is required").max(10000),
});

export const CONDITION_OPERATORS = ["EQUALS", "NOT_EQUALS", "GREATER_THAN", "LESS_THAN", "CONTAINS", "EXISTS"] as const;
export const COMBINATORS = ["AND", "OR"] as const;

export const conditionClauseSchema = z.strictObject({
  left: requiredUnknown("Left operand"),
  operator: z.enum(CONDITION_OPERATORS),
  right: z.unknown().optional(),
});

export const conditionConfigSchema = z.strictObject({
  combinator: z.enum(COMBINATORS).optional(),
  conditions: z.array(conditionClauseSchema).min(1, "Add at least one condition").max(20),
});

export const delayConfigSchema = z.strictObject({
  durationSeconds: z.number().int("Whole seconds only").min(1).max(604800),
});

export const nodeConfigSchemas = {
  MANUAL_TRIGGER: manualTriggerConfigSchema,
  WEBHOOK_TRIGGER: webhookTriggerConfigSchema,
  SCHEDULE_TRIGGER: scheduleTriggerConfigSchema,
  HTTP_REQUEST: httpRequestConfigSchema,
  TRANSFORM: transformConfigSchema,
  LOG: logConfigSchema,
  NOTIFICATION: notificationConfigSchema,
  CONDITION: conditionConfigSchema,
  DELAY: delayConfigSchema,
} as const satisfies Record<NodeType, z.ZodType>;

export const retryPolicySchema = z.strictObject({
  maxAttempts: z.number().int().min(1).max(10),
  initialIntervalMs: z.number().int().min(100).max(3_600_000).optional(),
  multiplier: z.number().min(1).max(10).optional(),
  maxIntervalMs: z.number().int().min(100).max(3_600_000).optional(),
});

export const nodeNameSchema = z.string().trim().min(1, "Name is required").max(100, "At most 100 characters");
export const timeoutSecondsSchema = z.number().int().min(1).max(300);

export const settingsSchema = z.strictObject({
  executionTimeoutSeconds: z.number().int().min(1).max(86400).optional(),
  maxConcurrentExecutions: z.number().int().min(1).max(50).optional(),
});

export type ConfigIssue = { path: string; message: string };

/** Validates a node config against the schema for its type. */
export function validateNodeConfig(type: NodeType, config: unknown): { success: true } | { success: false; issues: ConfigIssue[] } {
  const result = nodeConfigSchemas[type].safeParse(config);
  if (result.success) return { success: true };
  return {
    success: false,
    issues: result.error.issues.map((i) => ({ path: i.path.join("."), message: i.message })),
  };
}

/** Structural validation of a whole node (name, config, timeout, retry). */
export function validateNode(node: {
  type: NodeType;
  name: string;
  config: unknown;
  timeoutSeconds?: number;
  retryPolicy?: unknown;
}): ConfigIssue[] {
  const issues: ConfigIssue[] = [];
  const name = nodeNameSchema.safeParse(node.name);
  if (!name.success) issues.push({ path: "name", message: name.error.issues[0]?.message ?? "Invalid name" });
  const config = validateNodeConfig(node.type, node.config);
  if (!config.success) issues.push(...config.issues.map((i) => ({ path: `config.${i.path}`, message: i.message })));
  if (node.timeoutSeconds !== undefined && !timeoutSecondsSchema.safeParse(node.timeoutSeconds).success) {
    issues.push({ path: "timeoutSeconds", message: "Timeout must be 1–300 seconds" });
  }
  if (node.retryPolicy !== undefined && !retryPolicySchema.safeParse(node.retryPolicy).success) {
    issues.push({ path: "retryPolicy", message: "Invalid retry policy" });
  }
  return issues;
}

/** Default (schema-valid) configs for newly added nodes. */
export function defaultConfig(type: NodeType): Record<string, unknown> {
  switch (type) {
    case "MANUAL_TRIGGER":
      return {};
    case "WEBHOOK_TRIGGER":
      return {};
    case "SCHEDULE_TRIGGER":
      return { cron: "0 9 * * *", timezone: "UTC" };
    case "HTTP_REQUEST":
      return { method: "GET", url: "https://example.com" };
    case "TRANSFORM":
      return {};
    case "LOG":
      return { message: "Execution {{ execution.id }} reached this step" };
    case "NOTIFICATION":
      return { channel: "LOG", recipient: "operations", message: "Workflow {{ execution.workflowId }} notification" };
    case "CONDITION":
      return { combinator: "AND", conditions: [{ left: "{{ trigger.value }}", operator: "EXISTS" }] };
    case "DELAY":
      return { durationSeconds: 60 };
  }
}
