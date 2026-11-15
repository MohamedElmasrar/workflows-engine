/** Config form of the Transform node: source, pick, rename and set. */
import { z } from "zod";
import { fromItems, type KeyValue, keyValue, recordFromRows, stringItem, toItems, uniqueKeys } from "./form-helpers";
import { formatJson, formatLiteral, isPlainObject, jsonObjectText, parseJsonObject, parseLiteral } from "./literals";

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
