package io.workflowengine.trigger.application.port.out;

import io.workflowengine.trigger.domain.model.ScheduledTrigger;
import io.workflowengine.trigger.domain.model.WebhookEndpoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriggerRepository {

    /** Records the active trigger kind of a workflow; returns false if a newer version is already registered. */
    boolean registerVersion(UUID workflowId, int version, String kind);

    Optional<WebhookEndpoint> webhookByTokenHash(String tokenHash);

    Optional<WebhookEndpoint> webhookByWorkflow(UUID workflowId);

    void saveWebhook(WebhookEndpoint endpoint);

    Optional<ScheduledTrigger> scheduleByWorkflow(UUID workflowId);

    void saveSchedule(ScheduledTrigger schedule);

    /** Locks due, active schedules with SKIP LOCKED so concurrent replicas never fire the same schedule. */
    List<ScheduledTrigger> lockDueSchedules(Instant now, int limit);

    void deactivateAll(UUID workflowId);
}
