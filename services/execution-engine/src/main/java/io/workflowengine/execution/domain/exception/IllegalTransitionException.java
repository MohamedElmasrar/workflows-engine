package io.workflowengine.execution.domain.exception;

/** A state change not permitted by the execution or node state machine. Indicates a programming error. */
public class IllegalTransitionException extends DomainException {
    public IllegalTransitionException(String subject, Enum<?> from, Enum<?> to) {
        super("Illegal transition of " + subject + " from " + from + " to " + to);
    }
}
