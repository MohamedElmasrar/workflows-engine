package io.workflowengine.workflow.domain.exception;

/** Base type for business rule violations. Infrastructure failures are not modelled as domain exceptions. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
