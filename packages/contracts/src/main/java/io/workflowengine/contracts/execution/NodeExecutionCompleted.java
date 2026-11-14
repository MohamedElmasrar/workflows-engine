package io.workflowengine.contracts.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Successful attempt. {@code branch} is the selected output handle for branching nodes, otherwise null. */
public record NodeExecutionCompleted(
        UUID executionId,
        String nodeId,
        UUID attemptId,
        String workerId,
        JsonNode output,
        String branch,
        long durationMs,
        Instant completedAt) {}
