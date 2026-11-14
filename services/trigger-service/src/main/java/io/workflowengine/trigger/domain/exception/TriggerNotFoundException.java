package io.workflowengine.trigger.domain.exception;

public class TriggerNotFoundException extends RuntimeException {
    public TriggerNotFoundException(String message) {
        super(message);
    }
}
