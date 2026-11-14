package io.workflowengine.execution.application.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.execution.domain.model.Actor;
import io.workflowengine.execution.domain.model.TriggerType;
import java.util.UUID;

/** Command objects for the execution use cases (Command pattern). */
public final class ExecutionCommands {

    private ExecutionCommands() {}

    /** Start a run. {@code version == null} means the latest published version. */
    public record StartExecution(
            UUID executionId,
            UUID workflowId,
            Integer version,
            JsonNode input,
            TriggerType triggerType,
            String triggeredBy,
            String idempotencyKey,
            UUID retryOf) {}

    public record CancelExecution(Actor actor, UUID executionId) {}

    public record RetryExecution(Actor actor, UUID executionId) {}
}
