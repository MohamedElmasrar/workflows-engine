/** Config form of the Condition node: AND/OR over a list of typed comparisons. */
import { z } from "zod";
import { COMBINATORS, CONDITION_OPERATORS } from "../node-configs";
import { formatLiteral, isPlainObject, parseLiteral } from "./literals";

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
