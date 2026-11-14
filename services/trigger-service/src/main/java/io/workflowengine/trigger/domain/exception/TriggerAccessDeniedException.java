package io.workflowengine.trigger.domain.exception;

public class TriggerAccessDeniedException extends RuntimeException {
    public TriggerAccessDeniedException(String message) {
        super(message);
    }
}
