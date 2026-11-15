/** Webhook and schedule triggers of a workflow and their deliveries. */

export interface WebhookTrigger {
  url: string | null;
  active: boolean;
  requireSignature: boolean;
  requiredFields: string[];
  version: number;
}

export interface ScheduleTrigger {
  cron: string;
  timezone: string;
  active: boolean;
  nextFireAt: string | null;
  lastFiredAt: string | null;
  version: number;
}

export interface WorkflowTriggers {
  webhook: WebhookTrigger | null;
  schedule: ScheduleTrigger | null;
}

/** Shape observed from GET /api/v1/workflows/{id}/trigger-deliveries (not documented in detail). */
export interface TriggerDelivery {
  id: string;
  workflowId: string;
  triggerType: "WEBHOOK" | "SCHEDULE" | string;
  dedupeKey: string | null;
  executionId: string | null;
  executionStatus: string | null;
  payloadBytes: number | null;
  receivedAt: string;
}
