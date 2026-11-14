package io.workflowengine.contracts.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Immutable workflow version, carried in full so consumers do not need to call the workflow service. */
public record WorkflowVersionPublished(
        UUID workflowId,
        int version,
        String name,
        String ownerId,
        String publishedBy,
        Instant publishedAt,
        JsonNode definition) {}
