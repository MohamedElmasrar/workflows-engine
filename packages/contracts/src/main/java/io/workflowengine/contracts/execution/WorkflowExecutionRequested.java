package io.workflowengine.contracts.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Request to start an execution. The requester chooses {@code executionId}, making the request idempotent. */
public record WorkflowExecutionRequested(
        UUID executionId,
        UUID workflowId,
        int workflowVersion,
        String triggerType,
        String triggeredBy,
        JsonNode input,
        String idempotencyKey) {}
