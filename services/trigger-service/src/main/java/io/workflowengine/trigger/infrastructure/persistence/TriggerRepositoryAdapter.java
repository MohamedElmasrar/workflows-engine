package io.workflowengine.trigger.infrastructure.persistence;

import io.workflowengine.trigger.application.port.out.TriggerRepository;
import io.workflowengine.trigger.domain.model.ScheduledTrigger;
import io.workflowengine.trigger.domain.model.WebhookEndpoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TriggerRepositoryAdapter implements TriggerRepository {

    private final WebhookRegistrationJpaRepository webhooks;
    private final ScheduledTriggerJpaRepository schedules;
    private final JdbcTemplate jdbc;

    /**
     * SQL on purpose: an atomic "insert, or update only if this version is newer" so that out-of-order or concurrent
     * WorkflowVersionPublished events can never move the registration back to an older version.
     */
    @Override
    public boolean registerVersion(UUID workflowId, int version, String kind) {
        return jdbc.update(
                        """
                        insert into trigger_registration (workflow_id, workflow_version, trigger_kind) values (?, ?, ?)
                        on conflict (workflow_id) do update set workflow_version = excluded.workflow_version,
                            trigger_kind = excluded.trigger_kind, updated_at = now()
                        where trigger_registration.workflow_version < excluded.workflow_version
                        """,
                        workflowId,
                        version,
                        kind)
                == 1;
    }

    @Override
    public Optional<WebhookEndpoint> webhookByTokenHash(String tokenHash) {
        return webhooks.findByTokenHash(tokenHash).map(TriggerRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<WebhookEndpoint> webhookByWorkflow(UUID workflowId) {
        return webhooks.findByWorkflowId(workflowId).map(TriggerRepositoryAdapter::toDomain);
    }

    @Override
    public void saveWebhook(WebhookEndpoint w) {
        WebhookRegistrationEntity entity = webhooks.findById(w.id())
                .orElseGet(
                        () -> new WebhookRegistrationEntity(w.id(), w.workflowId(), w.tokenHash(), w.encryptedToken()));
        entity.setWorkflowVersion(w.workflowVersion());
        entity.setSecretEncrypted(w.encryptedSecret());
        entity.setRequireSignature(w.requireSignature());
        entity.setRequiredFields(List.copyOf(w.requiredFields()));
        entity.setActive(w.active());
        entity.setUpdatedAt(Instant.now());
        webhooks.save(entity);
    }

    @Override
    public Optional<ScheduledTrigger> scheduleByWorkflow(UUID workflowId) {
        return schedules.findByWorkflowId(workflowId).map(TriggerRepositoryAdapter::toDomain);
    }

    @Override
    public void saveSchedule(ScheduledTrigger s) {
        ScheduledTriggerEntity entity =
                schedules.findById(s.id()).orElseGet(() -> new ScheduledTriggerEntity(s.id(), s.workflowId()));
        entity.setWorkflowVersion(s.workflowVersion());
        entity.setCron(s.cron());
        entity.setTimezone(s.timezone());
        entity.setPayload(s.payload());
        entity.setNextFireAt(s.nextFireAt());
        entity.setLastFiredAt(s.lastFiredAt());
        entity.setActive(s.active());
        entity.setUpdatedAt(Instant.now());
        schedules.save(entity);
    }

    @Override
    public List<ScheduledTrigger> lockDueSchedules(Instant now, int limit) {
        return schedules.lockDue(now, limit).stream()
                .map(TriggerRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public void deactivateAll(UUID workflowId) {
        Instant now = Instant.now();
        webhooks.deactivate(workflowId, now);
        schedules.deactivate(workflowId, now);
    }

    private static WebhookEndpoint toDomain(WebhookRegistrationEntity e) {
        return new WebhookEndpoint(
                e.getId(),
                e.getWorkflowId(),
                e.getWorkflowVersion(),
                e.getTokenHash(),
                e.getTokenEncrypted(),
                e.getSecretEncrypted(),
                e.isRequireSignature(),
                List.copyOf(e.getRequiredFields()),
                e.isActive());
    }

    private static ScheduledTrigger toDomain(ScheduledTriggerEntity e) {
        return new ScheduledTrigger(
                e.getId(),
                e.getWorkflowId(),
                e.getWorkflowVersion(),
                e.getCron(),
                e.getTimezone(),
                e.getPayload(),
                e.getNextFireAt(),
                e.getLastFiredAt(),
                e.isActive());
    }
}
