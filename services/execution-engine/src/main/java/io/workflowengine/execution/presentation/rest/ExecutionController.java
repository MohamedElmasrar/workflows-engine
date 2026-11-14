package io.workflowengine.execution.presentation.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.workflowengine.execution.application.command.ExecutionCommands.CancelExecution;
import io.workflowengine.execution.application.command.ExecutionCommands.RetryExecution;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionFilter;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionSummary;
import io.workflowengine.execution.application.query.ExecutionQueries.HistoryEntryView;
import io.workflowengine.execution.application.query.ExecutionQueries.Page;
import io.workflowengine.execution.application.query.ExecutionQueries.Stats;
import io.workflowengine.execution.application.usecase.ExecutionQueryService;
import io.workflowengine.execution.application.usecase.ExecutionService;
import io.workflowengine.execution.application.usecase.StartResult;
import io.workflowengine.execution.domain.model.Actor;
import io.workflowengine.execution.domain.model.ExecutionStatus;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.ExecutionResponse;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.ExecutionSummaryResponse;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.PageResponse;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.StartExecutionRequest;
import io.workflowengine.execution.presentation.dto.ExecutionDtos.StatsResponse;
import io.workflowengine.platform.security.CurrentUser;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Executions", description = "Start, inspect, cancel and retry workflow executions")
public class ExecutionController {

    private final ExecutionService commands;
    private final ExecutionQueryService queries;

    public ExecutionController(ExecutionService commands, ExecutionQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping("/api/v1/workflows/{workflowId}/executions")
    @Operation(
            summary = "Start a manual execution of the latest (or a given) published version. "
                    + "An Idempotency-Key header makes retries of this request safe.")
    public ResponseEntity<ExecutionResponse> start(
            Authentication auth,
            @PathVariable UUID workflowId,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 200) String idempotencyKey,
            @RequestBody(required = false) StartExecutionRequest body) {
        Actor actor = actor(auth);
        StartResult result = commands.startManual(
                actor,
                workflowId,
                body == null ? null : body.version(),
                body == null ? null : body.input(),
                idempotencyKey);
        ExecutionResponse response = ExecutionMapper.toResponse(queries.get(actor, result.executionId()));
        if (!result.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/executions/" + result.executionId()))
                .body(response);
    }

    @GetMapping("/api/v1/executions")
    @Operation(summary = "Execution history with filters and pagination (newest first)")
    public PageResponse<ExecutionSummaryResponse> list(
            Authentication auth,
            @RequestParam(required = false) UUID workflowId,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ExecutionSummary> result = queries.search(
                actor(auth),
                new ExecutionFilter(workflowId, status == null ? null : status.name(), from, to, page, size));
        return new PageResponse<>(
                result.items().stream().map(ExecutionMapper::toSummary).toList(),
                result.page(),
                result.size(),
                result.totalItems(),
                (int) Math.ceil((double) result.totalItems() / result.size()));
    }

    @GetMapping("/api/v1/executions/stats")
    public StatsResponse stats(Authentication auth) {
        Actor actor = actor(auth);
        Stats stats = queries.stats(actor);
        return new StatsResponse(
                stats.executionsToday(),
                stats.completedToday(),
                stats.failedToday(),
                stats.running(),
                stats.pending(),
                queries.recent(actor, 10).stream()
                        .map(ExecutionMapper::toSummary)
                        .toList());
    }

    @GetMapping("/api/v1/executions/{id}")
    public ExecutionResponse get(Authentication auth, @PathVariable UUID id) {
        return ExecutionMapper.toResponse(queries.get(actor(auth), id));
    }

    @PostMapping("/api/v1/executions/{id}/cancel")
    public ExecutionResponse cancel(Authentication auth, @PathVariable UUID id) {
        Actor actor = actor(auth);
        commands.cancel(new CancelExecution(actor, id));
        return ExecutionMapper.toResponse(queries.get(actor, id));
    }

    @PostMapping("/api/v1/executions/{id}/retry")
    @Operation(summary = "Start a new execution with the same version and input")
    public ResponseEntity<ExecutionResponse> retry(Authentication auth, @PathVariable UUID id) {
        Actor actor = actor(auth);
        StartResult result = commands.retry(new RetryExecution(actor, id));
        return ResponseEntity.created(URI.create("/api/v1/executions/" + result.executionId()))
                .body(ExecutionMapper.toResponse(queries.get(actor, result.executionId())));
    }

    @GetMapping("/api/v1/executions/{id}/events")
    @Operation(summary = "Persisted state transitions after the given sequence number")
    public List<HistoryEntryView> events(
            Authentication auth,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "500") int limit) {
        return queries.history(actor(auth), id, after, limit);
    }

    static Actor actor(Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return new Actor(user.id(), user.admin());
    }
}
