/** Orders and formats the `details` map of a history entry for display in the timeline. */
import { formatAbsolute } from "@/lib/format";

const DETAIL_ORDER = ["errorCode", "errorMessage", "attempt", "nextAttemptAt", "workerId", "branch", "reason", "triggerType", "workflowVersion"];

export function detailEntries(details: Record<string, unknown>): [string, string][] {
  const keys = Object.keys(details).sort((a, b) => {
    const ia = DETAIL_ORDER.indexOf(a);
    const ib = DETAIL_ORDER.indexOf(b);
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
  });
  return keys.map((k) => {
    const v = details[k];
    const text = typeof v === "string" ? (k.endsWith("At") ? formatAbsolute(v) : v) : JSON.stringify(v);
    return [k, text];
  });
}
