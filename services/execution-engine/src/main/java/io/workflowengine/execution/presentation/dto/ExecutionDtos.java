package io.workflowengine.execution.presentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExecutionDtos {

    private ExecutionDtos() {}

    public record StartExecutionRequest(JsonNode input, Integer version) {}

    public record AttemptResponse(
            UUID attemptId,
            int number,
            String status,
            String workerId,
            Instant dispatchedAt,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorMessage) {}

    public record NodeErrorResponse(String code, String message, boolean retryable) {}

    public record NodeResponse(
            String nodeId,
            String nodeType,
            String name,
            String status,
            int attemptCount,
            JsonNode input,
            JsonNode output,
            String branch,
            NodeErrorResponse error,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            Instant nextAttemptAt,
            Instant resumeAt,
            List<AttemptResponse> attempts) {}

    public record ExecutionResponse(
            UUID id,
            UUID workflowId,
            String workflowName,
            int workflowVersion,
            String status,
            String triggerType,
            String triggeredBy,
            JsonNode input,
            JsonNode output,
            String errorCode,
            String errorMessage,
            UUID retryOf,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Instant deadlineAt,
            Long durationMs,
            long lastEventSequence,
            JsonNode definition,
            List<NodeResponse> nodes) {}

    public record ExecutionSummaryResponse(
            UUID id,
            UUID workflowId,
            String workflowName,
            int workflowVersion,
            String status,
            String triggerType,
            String triggeredBy,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            String errorCode,
            String errorMessage,
            UUID retryOf) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record StatsResponse(
            long executionsToday,
            long completedToday,
            long failedToday,
            long running,
            long pending,
            List<ExecutionSummaryResponse> recent) {}
}
