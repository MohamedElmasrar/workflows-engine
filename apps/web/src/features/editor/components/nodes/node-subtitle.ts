/** One-line summary of a node's configuration, shown under its name on the canvas. */
import { formatSeconds } from "@/lib/format";
import type { WorkflowFlowNode } from "../../lib/flow-types";
import { NODE_CATALOG } from "../../lib/node-catalog";

export function nodeSubtitle(node: WorkflowFlowNode["data"]): string {
  const c = node.config;
  switch (node.nodeType) {
    case "HTTP_REQUEST":
      return `${String(c.method ?? "GET")} ${String(c.url ?? "")}`;
    case "SCHEDULE_TRIGGER":
      return `${String(c.cron ?? "")} · ${String(c.timezone ?? "")}`;
    case "DELAY":
      return typeof c.durationSeconds === "number" ? `Wait ${formatSeconds(c.durationSeconds)}` : "";
    case "LOG":
      return String(c.message ?? "");
    case "NOTIFICATION":
      return `${String(c.channel ?? "")} → ${String(c.recipient ?? "")}`;
    case "CONDITION": {
      const n = Array.isArray(c.conditions) ? c.conditions.length : 0;
      return `${n} condition${n === 1 ? "" : "s"} (${String(c.combinator ?? "AND")})`;
    }
    case "WEBHOOK_TRIGGER":
      return c.requireSignature ? "Signed webhook" : "Webhook";
    case "MANUAL_TRIGGER":
      return "Run on demand";
    default:
      return NODE_CATALOG[node.nodeType].description;
  }
}
