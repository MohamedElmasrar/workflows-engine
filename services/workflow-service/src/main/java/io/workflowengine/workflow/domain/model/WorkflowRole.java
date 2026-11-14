package io.workflowengine.workflow.domain.model;

/** Per-workflow role. Roles are ordered: each role includes the permissions of the roles below it. */
public enum WorkflowRole {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean includes(WorkflowRole other) {
        return compareTo(other) >= 0;
    }
}
