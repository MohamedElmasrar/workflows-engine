package io.workflowengine.worker.domain;

import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import java.time.Duration;
import java.util.UUID;

public final class Contexts {

    private Contexts() {}

    public static NodeExecutionContext context() {
        return context(NodeType.LOG, Duration.ofSeconds(5));
    }

    public static NodeExecutionContext context(NodeType type, Duration timeout) {
        return new NodeExecutionContext(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.randomUUID(),
                "node",
                type,
                UUID.randomUUID(),
                1,
                timeout);
    }
}
