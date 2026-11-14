package io.workflowengine.trigger.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ScheduledTrigger(
        UUID id,
        UUID workflowId,
        int workflowVersion,
        String cron,
        String timezone,
        JsonNode payload,
        Instant nextFireAt,
        Instant lastFiredAt,
        boolean active) {}
