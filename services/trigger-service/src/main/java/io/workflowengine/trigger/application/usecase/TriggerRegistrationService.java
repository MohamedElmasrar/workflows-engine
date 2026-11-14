package io.workflowengine.trigger.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.trigger.application.port.out.AccessRepository;
import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import io.workflowengine.trigger.application.port.out.MessageDeduplicator;
import io.workflowengine.trigger.application.port.out.SecretProtector;
import io.workflowengine.trigger.application.port.out.TriggerRepository;
import io.workflowengine.trigger.domain.model.ScheduledTrigger;
import io.workflowengine.trigger.domain.model.TriggerSpec;
import io.workflowengine.trigger.domain.model.WebhookEndpoint;
import io.workflowengine.trigger.domain.service.SchedulePolicy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps trigger registrations in sync with published workflow versions. Publishing a version activates the trigger
 * type of that version and deactivates the others; archiving deactivates everything. Webhook tokens are stable across
 * versions so external callers do not need to be reconfigured.
 */
@Transactional
public class TriggerRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(TriggerRegistrationService.class);
    public static final String CONSUMER = "workflow-events";

    private final TriggerRepository triggers;
    private final AccessRepository access;
    private final DeliveryRepository deliveries;
    private final SecretProtector secrets;
    private final MessageDeduplicator dedup;
    private final SchedulePolicy schedules;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TriggerRegistrationService(
            TriggerRepository triggers,
            AccessRepository access,
            DeliveryRepository deliveries,
            SecretProtector secrets,
            MessageDeduplicator dedup,
            SchedulePolicy schedules,
            Clock clock) {
        this.triggers = triggers;
        this.access = access;
        this.deliveries = deliveries;
        this.secrets = secrets;
        this.dedup = dedup;
        this.schedules = schedules;
        this.clock = clock;
    }

    public void onVersionPublished(UUID eventId, UUID workflowId, int version, JsonNode definition) {
        if (!dedup.firstDelivery(CONSUMER, eventId)) {
            return;
        }
        TriggerSpec spec = TriggerSpec.fromDefinition(definition);
        if (!triggers.registerVersion(workflowId, version, spec.kind().name())) {
            log.info("Ignoring out-of-order version {} of workflow {}", version, workflowId);
            return;
        }
        triggers.deactivateAll(workflowId);
        switch (spec.kind()) {
            case WEBHOOK -> activateWebhook(workflowId, version, spec);
            case SCHEDULE -> activateSchedule(workflowId, version, spec);
            case MANUAL -> {
                // nothing to register: manual runs go straight to the execution engine
            }
        }
    }

    public void onArchived(UUID eventId, UUID workflowId) {
        if (dedup.firstDelivery(CONSUMER, eventId)) {
            triggers.deactivateAll(workflowId);
        }
    }

    public void onAccessUpdated(UUID eventId, UUID workflowId, String ownerId, long aclRevision, Map<String, String> roles) {
        if (dedup.firstDelivery(CONSUMER, eventId)) {
            access.replace(workflowId, ownerId, aclRevision, roles);
        }
    }

    public void onExecutionStatus(UUID eventId, UUID executionId, String status) {
        if (dedup.firstDelivery("execution-lifecycle", eventId)) {
            deliveries.updateExecutionStatus(executionId, status);
        }
    }

    private void activateWebhook(UUID workflowId, int version, TriggerSpec spec) {
        WebhookEndpoint existing = triggers.webhookByWorkflow(workflowId).orElse(null);
        String encryptedToken;
        String tokenHash;
        String encryptedSecret;
        UUID id;
        if (existing == null) {
            String token = secrets.newToken();
            id = UUID.randomUUID();
            tokenHash = secrets.hashToken(token);
            encryptedToken = secrets.encrypt(token);
            encryptedSecret = secrets.encrypt(newSecret());
        } else {
            id = existing.id();
            tokenHash = existing.tokenHash();
            encryptedToken = existing.encryptedToken();
            encryptedSecret = existing.encryptedSecret();
        }
        triggers.saveWebhook(new WebhookEndpoint(
                id, workflowId, version, tokenHash, encryptedToken, encryptedSecret, spec.requireSignature(),
                spec.requiredFields(), true));
    }

    private void activateSchedule(UUID workflowId, int version, TriggerSpec spec) {
        Instant now = clock.instant();
        ScheduledTrigger existing = triggers.scheduleByWorkflow(workflowId).orElse(null);
        boolean sameSchedule = existing != null
                && Objects.equals(existing.cron(), spec.cron())
                && Objects.equals(existing.timezone(), spec.timezone());
        Instant next = sameSchedule && existing.nextFireAt() != null
                ? existing.nextFireAt()
                : schedules.nextFireAfter(spec.cron(), spec.timezone(), now);
        triggers.saveSchedule(new ScheduledTrigger(
                existing == null ? UUID.randomUUID() : existing.id(),
                workflowId,
                version,
                spec.cron(),
                spec.timezone(),
                spec.schedulePayload(),
                next,
                existing == null ? null : existing.lastFiredAt(),
                true));
    }

    String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String rotateSecret(UUID workflowId) {
        WebhookEndpoint endpoint = triggers.webhookByWorkflow(workflowId)
                .orElseThrow(() -> new io.workflowengine.trigger.domain.exception.TriggerNotFoundException(
                        "Workflow has no webhook trigger"));
        String secret = newSecret();
        triggers.saveWebhook(new WebhookEndpoint(
                endpoint.id(), endpoint.workflowId(), endpoint.workflowVersion(), endpoint.tokenHash(),
                endpoint.encryptedToken(), secrets.encrypt(secret), endpoint.requireSignature(), endpoint.requiredFields(),
                endpoint.active()));
        return secret;
    }
}
