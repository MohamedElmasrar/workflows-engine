package io.workflowengine.workflow.domain.exception;

/** Operation not allowed in the workflow's current lifecycle state (e.g. editing an archived workflow). */
public class WorkflowStateException extends DomainException {

    public WorkflowStateException(String message) {
        super(message);
    }
}
