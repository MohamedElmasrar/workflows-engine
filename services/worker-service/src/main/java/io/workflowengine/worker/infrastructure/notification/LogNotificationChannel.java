package io.workflowengine.worker.infrastructure.notification;

import io.workflowengine.worker.domain.port.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development-safe channel that only writes the notification to the log. */
public class LogNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger("workflow.notification");

    @Override
    public String channel() {
        return "LOG";
    }

    @Override
    public String recipientProblem(String recipient) {
        return recipient.isBlank() ? "Recipient is required" : null;
    }

    @Override
    public void send(Notification n) {
        log.info("Notification to {}: {} - {}", n.recipient(), n.subject(), n.message());
    }
}
