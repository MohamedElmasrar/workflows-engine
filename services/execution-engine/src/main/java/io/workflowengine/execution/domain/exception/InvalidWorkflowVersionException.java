package io.workflowengine.execution.domain.exception;

/** The published definition violates an invariant the engine depends on. */
public class InvalidWorkflowVersionException extends DomainException {
    public InvalidWorkflowVersionException(String message) {
        super(message);
    }
}
