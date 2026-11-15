/** Config form of the HTTP Request node: method, URL, headers, query parameters and body. */
import { z } from "zod";
import { HTTP_METHODS } from "../node-configs";
import { keyValue, recordFromRows, uniqueKeys } from "./form-helpers";
import { formatJson, isPlainObject, jsonAnyText } from "./literals";

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
