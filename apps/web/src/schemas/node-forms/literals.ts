/** Text <-> JSON conversions for form inputs that may hold "any JSON" or a template string. */
import { z } from "zod";

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

export function isPlainObject(value: unknown): value is Record<string, unknown> {
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

export function parseJsonObject(text: string): Record<string, unknown> | undefined {
  if (text.trim() === "") return undefined;
  const parsed: unknown = JSON.parse(text);
  return isPlainObject(parsed) ? parsed : undefined;
}
