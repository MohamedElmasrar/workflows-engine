package io.workflowengine.workflow.domain.model;

/** Operations subject to workflow-level authorization, with the minimum role each requires. */
public enum WorkflowAction {
    VIEW(WorkflowRole.VIEWER),
    EDIT(WorkflowRole.EDITOR),
    PUBLISH(WorkflowRole.EDITOR),
    EXECUTE(WorkflowRole.EDITOR),
    MANAGE_PERMISSIONS(WorkflowRole.OWNER),
    ARCHIVE(WorkflowRole.OWNER);

    private final WorkflowRole minimumRole;

    WorkflowAction(WorkflowRole minimumRole) {
        this.minimumRole = minimumRole;
    }

    public WorkflowRole minimumRole() {
        return minimumRole;
    }
}
