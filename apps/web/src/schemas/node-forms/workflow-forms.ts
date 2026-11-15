/** Workflow-level forms: settings panel and the create-workflow dialog. */
import { z } from "zod";
import { optionalInt } from "./form-helpers";

export const workflowSettingsFormSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(100, "At most 100 characters"),
  description: z.string().max(1000, "At most 1000 characters"),
  executionTimeoutSeconds: optionalInt(1, 86400, "Execution timeout"),
  maxConcurrentExecutions: optionalInt(1, 50, "Max concurrent executions"),
});
export type WorkflowSettingsForm = z.infer<typeof workflowSettingsFormSchema>;

export const createWorkflowSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(100, "At most 100 characters"),
  description: z.string().max(1000, "At most 1000 characters"),
});
export type CreateWorkflowForm = z.infer<typeof createWorkflowSchema>;
