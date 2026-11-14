package io.workflowengine.worker.domain.model;

import io.workflowengine.contracts.NodeType;
import java.time.Duration;
import java.util.UUID;

/** Identity and limits of the attempt being executed. Configuration is passed to handlers separately. */
public record NodeExecutionContext(
        UUID executionId,
        UUID workflowId,
        String nodeId,
        NodeType nodeType,
        UUID attemptId,
        int attemptNumber,
        Duration timeout) {

    /** Stable across attempts of the same node, so cooperating APIs can deduplicate retried requests. */
    public String idempotencyKey() {
        return executionId + ":" + nodeId;
    }
}
