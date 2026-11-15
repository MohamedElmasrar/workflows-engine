/** Building blocks shared by the node form schemas: list items, key/value rows, numeric fields. */
import { z } from "zod";

export const stringItem = z.object({ value: z.string() });
export type StringItem = z.infer<typeof stringItem>;
export const toItems = (values: unknown): StringItem[] => (Array.isArray(values) ? values.map((v) => ({ value: String(v) })) : []);
export const fromItems = (items: StringItem[]): string[] => items.map((i) => i.value.trim()).filter((v) => v !== "");

export const keyValue = z.object({ key: z.string(), value: z.string() });
export type KeyValue = z.infer<typeof keyValue>;

export function uniqueKeys(rows: { key: string }[], ctx: z.RefinementCtx, field: string) {
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

export function recordFromRows<R extends { key: string }, T>(rows: R[], value: (row: R) => T): Record<string, T> | undefined {
  if (rows.length === 0) return undefined;
  const out: Record<string, T> = {};
  for (const row of rows) out[row.key.trim()] = value(row);
  return out;
}

export const optionalInt = (min: number, max: number, label: string) =>
  z
    .number({ error: `${label} must be a number` })
    .int(`${label} must be a whole number`)
    .min(min, `${label} must be at least ${min}`)
    .max(max, `${label} must be at most ${max}`)
    .optional();

/** RHF `setValueAs` for optional numeric inputs: empty → undefined. */
export function optionalNumber(value: unknown): number | undefined {
  if (value === "" || value === null || value === undefined) return undefined;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isNaN(n) ? Number.NaN : n;
}
