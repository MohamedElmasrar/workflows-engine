package io.workflowengine.worker.infrastructure.notification;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.port.NotificationChannel;
import jakarta.mail.internet.MimeMessage;
import java.util.regex.Pattern;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** E-mail adapter over SMTP (Mailpit in development), protected by a circuit breaker. */
public class EmailNotificationChannel implements NotificationChannel {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}\\.[^@\\s]{2,}$");

    private final JavaMailSender sender;
    private final CircuitBreaker breaker;
    private final String from;

    public EmailNotificationChannel(JavaMailSender sender, CircuitBreaker breaker, String from) {
        this.sender = sender;
        this.breaker = breaker;
        this.from = from;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public String recipientProblem(String recipient) {
        return EMAIL.matcher(recipient).matches() ? null : "Invalid e-mail address '" + recipient + "'";
    }

    @Override
    public void send(Notification n) {
        try {
            breaker.executeRunnable(() -> {
                try {
                    MimeMessage message = sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                    helper.setFrom(from);
                    helper.setTo(n.recipient());
                    helper.setSubject(n.subject());
                    helper.setText(n.message());
                    message.setHeader("X-Workflow-Idempotency-Key", n.idempotencyKey());
                    sender.send(message);
                } catch (jakarta.mail.MessagingException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (CallNotPermittedException e) {
            throw new NodeExecutionException("CIRCUIT_OPEN", "E-mail channel temporarily unavailable", true);
        } catch (MailException | IllegalStateException e) {
            throw new NodeExecutionException("NOTIFICATION_FAILED", "E-mail delivery failed: " + e.getClass().getSimpleName(), true, e);
        }
    }
}
