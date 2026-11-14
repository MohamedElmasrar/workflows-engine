package io.workflowengine.contracts.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Task for a worker. {@code config} has templates already resolved by the engine. */
public record NodeExecutionScheduled(
        UUID executionId,
        UUID workflowId,
        String nodeId,
        String nodeType,
        UUID attemptId,
        int attemptNumber,
        long timeoutMs,
        JsonNode config) {}
