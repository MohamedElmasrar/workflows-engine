package io.workflowengine.execution.domain.exception;

public class ExecutionNotFoundException extends DomainException {
    public ExecutionNotFoundException(String what) {
        super(what + " was not found");
    }
}
