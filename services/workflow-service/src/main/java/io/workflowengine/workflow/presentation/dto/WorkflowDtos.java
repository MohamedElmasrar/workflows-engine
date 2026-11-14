package io.workflowengine.workflow.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.workflow.domain.model.WorkflowRole;
import io.workflowengine.workflow.domain.model.WorkflowStatus;
import io.workflowengine.workflow.domain.service.ValidationIssue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** External REST contract of the workflow service. Never exposes JPA entities. */
public final class WorkflowDtos {

    private WorkflowDtos() {}

    public record CreateWorkflowRequest(
            @NotBlank @Size(max = 100) String name, @Size(max = 1000) String description, JsonNode definition) {}

    public record UpdateWorkflowRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @NotNull JsonNode definition,
            @NotNull Long revision) {}

    public record ValidateRequest(JsonNode definition) {}

    public record GrantPermissionRequest(@NotNull WorkflowRole role) {}

    public record WorkflowResponse(
            UUID id,
            String name,
            String description,
            WorkflowStatus status,
            String ownerId,
            WorkflowRole role,
            long revision,
            Integer latestVersion,
            boolean hasUnpublishedChanges,
            JsonNode definition,
            Instant createdAt,
            Instant updatedAt) {}

    public record WorkflowSummaryResponse(
            UUID id,
            String name,
            String description,
            WorkflowStatus status,
            String ownerId,
            WorkflowRole role,
            Integer latestVersion,
            boolean hasUnpublishedChanges,
            String triggerType,
            int nodeCount,
            Instant createdAt,
            Instant updatedAt) {}

    public record WorkflowVersionResponse(
            UUID workflowId, int version, String name, String publishedBy, Instant publishedAt, JsonNode definition) {}

    public record ValidationResponse(boolean valid, List<ValidationIssue> errors) {}

    public record PermissionResponse(String userId, String username, WorkflowRole role, Instant grantedAt) {}

    public record StatsResponse(long totalWorkflows, long activeWorkflows) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
}
