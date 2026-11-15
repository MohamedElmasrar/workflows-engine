/** Workflows, versions, validation results and sharing. */
import type { WorkflowDefinition } from "./definition";

export type WorkflowStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type WorkflowRole = "OWNER" | "EDITOR" | "VIEWER";

export interface Workflow {
  id: string;
  name: string;
  description: string | null;
  status: WorkflowStatus;
  ownerId: string;
  role: WorkflowRole;
  revision: number;
  latestVersion: number | null;
  hasUnpublishedChanges: boolean;
  definition: WorkflowDefinition;
  createdAt: string;
  updatedAt: string;
}

export type WorkflowSummary = Omit<Workflow, "definition" | "revision"> & {
  triggerType: string | null;
  nodeCount: number;
};

export interface WorkflowVersion {
  workflowId: string;
  version: number;
  name: string;
  publishedBy: string;
  publishedAt: string;
  definition: WorkflowDefinition | null;
}

export interface WorkflowStats {
  totalWorkflows: number;
  activeWorkflows: number;
}

export interface ValidationIssue {
  code: string;
  message: string;
  nodeId: string | null;
  edgeId: string | null;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationIssue[];
}

export interface Permission {
  userId: string;
  username: string;
  role: WorkflowRole;
  grantedAt: string;
}
