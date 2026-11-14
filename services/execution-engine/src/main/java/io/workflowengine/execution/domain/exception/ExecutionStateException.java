package io.workflowengine.execution.domain.exception;

/** The requested operation is not valid in the execution's current state (e.g. cancelling a finished run). */
public class ExecutionStateException extends DomainException {
    public ExecutionStateException(String message) {
        super(message);
    }
}
