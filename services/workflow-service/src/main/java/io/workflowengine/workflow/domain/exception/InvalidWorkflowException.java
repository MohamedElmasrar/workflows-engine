package io.workflowengine.workflow.domain.exception;

import io.workflowengine.workflow.domain.service.ValidationResult;

public class InvalidWorkflowException extends DomainException {

    private final ValidationResult result;

    public InvalidWorkflowException(ValidationResult result) {
        super("The workflow definition is invalid (" + result.errors().size() + " error(s))");
        this.result = result;
    }

    public ValidationResult result() {
        return result;
    }
}
