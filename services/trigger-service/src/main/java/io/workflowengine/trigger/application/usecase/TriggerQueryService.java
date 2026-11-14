package io.workflowengine.trigger.application.usecase;

import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import io.workflowengine.trigger.application.port.out.SecretProtector;
import io.workflowengine.trigger.application.port.out.TriggerRepository;
import io.workflowengine.trigger.application.usecase.Authorization.Caller;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class TriggerQueryService {

    public record WebhookView(String url, boolean active, boolean requireSignature, List<String> requiredFields, int version) {}

    public record ScheduleView(String cron, String timezone, boolean active, Instant nextFireAt, Instant lastFiredAt, int version) {}

    public record TriggersView(WebhookView webhook, ScheduleView schedule) {}

    private final TriggerRepository triggers;
    private final DeliveryRepository deliveries;
    private final SecretProtector secrets;
    private final Authorization authorization;
    private final String publicBaseUrl;

    public TriggerQueryService(
            TriggerRepository triggers,
            DeliveryRepository deliveries,
            SecretProtector secrets,
            Authorization authorization,
            String publicBaseUrl) {
        this.triggers = triggers;
        this.deliveries = deliveries;
        this.secrets = secrets;
        this.authorization = authorization;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** The webhook URL contains the secret token, so it is only shown to owners and editors. */
    public TriggersView triggers(UUID workflowId, Caller caller) {
        String role = authorization.requireView(workflowId, caller);
        boolean canSeeSecrets = role.equals("OWNER") || role.equals("EDITOR");
        WebhookView webhook = triggers.webhookByWorkflow(workflowId)
                .map(w -> new WebhookView(
                        canSeeSecrets ? publicBaseUrl + "/api/v1/webhooks/" + secrets.decrypt(w.encryptedToken()) : null,
                        w.active(), w.requireSignature(), w.requiredFields(), w.workflowVersion()))
                .orElse(null);
        ScheduleView schedule = triggers.scheduleByWorkflow(workflowId)
                .map(s -> new ScheduleView(s.cron(), s.timezone(), s.active(), s.nextFireAt(), s.lastFiredAt(), s.workflowVersion()))
                .orElse(null);
        return new TriggersView(webhook, schedule);
    }

    public List<DeliveryRepository.Delivery> deliveries(UUID workflowId, Caller caller, int limit) {
        authorization.requireView(workflowId, caller);
        return deliveries.recent(workflowId, Math.min(limit, 100));
    }
}
