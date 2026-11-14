package io.workflowengine.execution.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.contracts.NodeType;
import java.util.UUID;

/** Work item for a worker; {@code config} has all templates resolved. */
public record NodeTask(
        UUID executionId,
        UUID workflowId,
        String nodeId,
        NodeType nodeType,
        UUID attemptId,
        int attemptNumber,
        long timeoutMs,
        JsonNode config) {}
