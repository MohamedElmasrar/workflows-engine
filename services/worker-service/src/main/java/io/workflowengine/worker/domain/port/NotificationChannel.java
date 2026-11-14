package io.workflowengine.worker.domain.port;

/** Adapter for one notification transport (e-mail, log, ...). */
public interface NotificationChannel {

    String channel();

    /** Validates the recipient for this channel; returns null if valid, otherwise a reason. */
    String recipientProblem(String recipient);

    void send(Notification notification);

    record Notification(String recipient, String subject, String message, String idempotencyKey) {}
}
