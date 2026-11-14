package io.workflowengine.execution.application.usecase;

import java.util.UUID;

/** {@code created == false} when an idempotency key matched an existing execution. */
public record StartResult(UUID executionId, boolean created) {}
