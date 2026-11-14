package io.workflowengine.execution.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.contracts.NodeType;
import java.time.Duration;

/** A node of the immutable workflow version being executed. */
public record GraphNode(
        String id, NodeType type, String name, JsonNode config, Duration timeout, RetryPolicy retryPolicy) {

    /** Trigger and DELAY nodes are handled by the engine; everything else is dispatched to workers. */
    public boolean executedByWorker() {
        return !type.isTrigger() && type != NodeType.DELAY;
    }
}
