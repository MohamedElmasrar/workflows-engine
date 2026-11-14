package io.workflowengine.workflow.application.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.workflow.domain.model.WorkflowRole;
import java.util.UUID;

/** Command objects accepted by {@code WorkflowCommandService}. */
public final class WorkflowCommands {

    private WorkflowCommands() {}

    public record CreateWorkflow(String name, String description, JsonNode definition) {}

    public record UpdateWorkflow(UUID workflowId, long expectedRevision, String name, String description, JsonNode definition) {}

    public record GrantPermission(UUID workflowId, String username, WorkflowRole role) {}

    public record RevokePermission(UUID workflowId, String username) {}
}
