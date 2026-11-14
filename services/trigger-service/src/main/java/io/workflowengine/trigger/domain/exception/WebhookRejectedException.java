package io.workflowengine.trigger.domain.exception;

/** A webhook request that must not start an execution. {@code status} is the HTTP status to answer with. */
public class WebhookRejectedException extends RuntimeException {

    public enum Reason {
        NOT_FOUND(404),
        INVALID_SIGNATURE(401),
        UNSUPPORTED_MEDIA_TYPE(415),
        INVALID_PAYLOAD(422),
        PAYLOAD_TOO_LARGE(413);

        public final int status;

        Reason(int status) {
            this.status = status;
        }
    }

    private final Reason reason;

    public WebhookRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
