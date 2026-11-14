package io.workflowengine.contracts.execution;

import java.time.Instant;
import java.util.UUID;

public record NodeExecutionStarted(
        UUID executionId, String nodeId, UUID attemptId, String workerId, Instant startedAt) {}
