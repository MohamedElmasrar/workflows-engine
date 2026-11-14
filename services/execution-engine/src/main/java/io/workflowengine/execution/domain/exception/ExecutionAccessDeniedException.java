package io.workflowengine.execution.domain.exception;

public class ExecutionAccessDeniedException extends DomainException {
    public ExecutionAccessDeniedException(String message) {
        super(message);
    }
}
