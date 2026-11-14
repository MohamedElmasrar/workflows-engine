import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import type { WorkflowDefinition } from "@/lib/api/types";

/** The realistic demo definition shipped with the repo (scripts/order-webhook-demo.json). Tests run from apps/web. */
export function loadDemoDefinition(): WorkflowDefinition {
  const path = resolve(process.cwd(), "../../scripts/order-webhook-demo.json");
  return JSON.parse(readFileSync(path, "utf8")) as WorkflowDefinition;
}
