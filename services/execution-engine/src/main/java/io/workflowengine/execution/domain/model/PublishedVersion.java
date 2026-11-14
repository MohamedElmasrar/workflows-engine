package io.workflowengine.execution.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Local copy of an immutable workflow version, maintained from WorkflowVersionPublished events. */
public record PublishedVersion(UUID workflowId, int version, String name, JsonNode definition) {}
