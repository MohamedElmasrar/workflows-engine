package io.workflowengine.contracts.execution;

import java.time.Instant;
import java.util.UUID;

public record NodeExecutionFailed(
        UUID executionId,
        String nodeId,
        UUID attemptId,
        String workerId,
        NodeError error,
        long durationMs,
        Instant failedAt) {}
