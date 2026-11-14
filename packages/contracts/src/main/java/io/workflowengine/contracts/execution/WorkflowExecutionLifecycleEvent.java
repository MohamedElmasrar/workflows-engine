package io.workflowengine.contracts.execution;

import java.time.Instant;
import java.util.UUID;

/** Payload of WorkflowExecution{Started,Completed,Failed,Cancelled,TimedOut}. */
public record WorkflowExecutionLifecycleEvent(
        UUID executionId,
        UUID workflowId,
        int workflowVersion,
        String status,
        Instant occurredAt,
        ExecutionError error) {

    public record ExecutionError(String code, String message) {}
}
