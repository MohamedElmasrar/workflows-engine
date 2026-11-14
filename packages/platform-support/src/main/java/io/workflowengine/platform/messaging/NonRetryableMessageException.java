package io.workflowengine.platform.messaging;

/** Thrown by consumers for messages that can never be processed; they are sent straight to the dead-letter topic. */
public class NonRetryableMessageException extends RuntimeException {

    public NonRetryableMessageException(String message) {
        super(message);
    }

    public NonRetryableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
