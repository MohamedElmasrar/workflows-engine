package io.workflowengine.workflow.domain.model;

/** DRAFT: never published. ACTIVE: has at least one published version. ARCHIVED: read-only, triggers disabled. */
public enum WorkflowStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
