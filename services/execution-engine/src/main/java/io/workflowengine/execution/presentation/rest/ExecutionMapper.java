package io.workflowengine.execution.presentation.rest;

import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionSummary;
import io.workflowengine.execution.application.usecase.ExecutionQueryService;
import io.workflowengine.execution.domain.model.NodeExecution;
import io.workflowengine.execution.domain.model.NodeStatus;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import io.workflowengine.execution.domain.policy.SensitiveDataMasker;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.AttemptResponse;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.ExecutionResponse;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.ExecutionSummaryResponse;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.NodeErrorResponse;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.NodeResponse;
import java.time.Duration;
import java.time.Instant;

/** Maps domain/read models to API DTOs. All user data passes through the sensitive-data masker. */
final class ExecutionMapper {

    private ExecutionMapper() {}

    static ExecutionResponse toResponse(ExecutionQueryService.Detail detail) {
        WorkflowExecution e = detail.execution();
        WorkflowExecution.State s = e.state();
        return new ExecutionResponse(
                s.id(),
                s.workflowId(),
                s.workflowName(),
                s.workflowVersion(),
                s.status().name(),
                s.triggerType().name(),
                s.triggeredBy(),
                SensitiveDataMasker.mask(s.input()),
                SensitiveDataMasker.mask(s.output()),
                s.error() == null ? null : s.error().code(),
                s.error() == null ? null : s.error().message(),
                s.retryOf(),
                s.createdAt(),
                s.startedAt(),
                s.finishedAt(),
                s.deadlineAt(),
                duration(s.startedAt(), s.finishedAt()),
                detail.lastSequence(),
                detail.version().definition(),
                e.nodes().stream().map(n -> toNode(e, n)).toList());
    }

    private static NodeResponse toNode(WorkflowExecution e, NodeExecution n) {
        return new NodeResponse(
                n.nodeId(),
                n.nodeType(),
                e.graph().node(n.nodeId()).name(),
                n.status().name(),
                n.attemptCount(),
                SensitiveDataMasker.mask(n.input()),
                SensitiveDataMasker.mask(n.output()),
                n.branch(),
                n.error() == null ? null : new NodeErrorResponse(n.error().code(), n.error().message(), n.error().retryable()),
                n.startedAt(),
                n.finishedAt(),
                duration(n.startedAt(), n.finishedAt()),
                n.status() == NodeStatus.READY ? n.availableAt() : null,
                n.resumeAt(),
                n.attempts().stream()
                        .map(a -> new AttemptResponse(
                                a.attemptId(), a.number(), a.status().name(), a.workerId(), a.dispatchedAt(),
                                a.startedAt(), a.finishedAt(), a.errorCode(), a.errorMessage()))
                        .toList());
    }

    static ExecutionSummaryResponse toSummary(ExecutionSummary s) {
        return new ExecutionSummaryResponse(
                s.id(), s.workflowId(), s.workflowName(), s.workflowVersion(), s.status(), s.triggerType(),
                s.triggeredBy(), s.createdAt(), s.startedAt(), s.finishedAt(), duration(s.startedAt(), s.finishedAt()),
                s.errorCode(), s.errorMessage(), s.retryOf());
    }

    private static Long duration(Instant start, Instant end) {
        return start == null || end == null ? null : Duration.between(start, end).toMillis();
    }
}
