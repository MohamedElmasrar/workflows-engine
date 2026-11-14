package io.workflowengine.workflow.domain.exception;

import io.workflowengine.workflow.domain.model.WorkflowAction;

public class WorkflowAccessDeniedException extends DomainException {

    public WorkflowAccessDeniedException(WorkflowAction action) {
        super("You are not allowed to " + action.name().toLowerCase().replace('_', ' ') + " this workflow");
    }
}
