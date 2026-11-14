package io.workflowengine.workflow.domain.exception;

import java.util.UUID;

public class WorkflowNotFoundException extends DomainException {

    public WorkflowNotFoundException(UUID id) {
        super("Workflow " + id + " was not found");
    }

    public WorkflowNotFoundException(UUID id, int version) {
        super("Version " + version + " of workflow " + id + " was not found");
    }
}
