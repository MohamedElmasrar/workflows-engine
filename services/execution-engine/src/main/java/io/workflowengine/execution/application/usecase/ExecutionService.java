package io.workflowengine.execution.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.workflowengine.execution.application.command.ExecutionCommands.CancelExecution;
import io.workflowengine.execution.application.command.ExecutionCommands.RetryExecution;
import io.workflowengine.execution.application.command.ExecutionCommands.StartExecution;
import io.workflowengine.execution.application.port.out.ExecutionEffectPublisher;
import io.workflowengine.execution.application.port.out.ExecutionMetrics;
import io.workflowengine.execution.application.port.out.ExecutionRepository;
import io.workflowengine.execution.application.port.out.MessageDeduplicator;
import io.workflowengine.execution.application.port.out.TransactionRunner;
import io.workflowengine.execution.application.port.out.WorkflowCatalog;
import io.workflowengine.execution.domain.exception.ExecutionNotFoundException;
import io.workflowengine.execution.domain.exception.ExecutionStateException;
import io.workflowengine.execution.domain.exception.InvalidWorkflowVersionException;
import io.workflowengine.execution.domain.model.Actor;
import io.workflowengine.execution.domain.model.EngineSettings;
import io.workflowengine.execution.domain.model.ExecutionError;
import io.workflowengine.execution.domain.model.ExecutionGraph;
import io.workflowengine.execution.domain.model.ExecutionStatus;
import io.workflowengine.execution.domain.model.NodeFailure;
import io.workflowengine.execution.domain.model.NodeExecution;
import io.workflowengine.execution.domain.model.PublishedVersion;
import io.workflowengine.execution.domain.model.ResultOutcome;
import io.workflowengine.execution.domain.model.TriggerType;
import io.workflowengine.execution.domain.model.WorkflowAccess;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import io.workflowengine.execution.domain.policy.ExecutionAccessPolicy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Execution use cases. Every operation loads the aggregate, applies one domain method and persists state, history and
 * outbox effects in a single transaction (re-run on optimistic-lock conflicts).
 */
public class ExecutionService {

    public static final int MAX_INPUT_BYTES = 256 * 1024;
    public static final String REQUESTS_CONSUMER = "execution-requests";
    public static final String RESULTS_CONSUMER = "node-results";

    private final ExecutionRepository executions;
    private final WorkflowCatalog catalog;
    private final ExecutionEffectPublisher publisher;
    private final ExecutionMetrics metrics;
    private final TransactionRunner tx;
    private final MessageDeduplicator deduplicator;
    private final EngineSettings settings;
    private final Clock clock;

    public ExecutionService(
            ExecutionRepository executions,
            WorkflowCatalog catalog,
            ExecutionEffectPublisher publisher,
            ExecutionMetrics metrics,
            TransactionRunner tx,
            MessageDeduplicator deduplicator,
            EngineSettings settings,
            Clock clock) {
        this.executions = executions;
        this.catalog = catalog;
        this.publisher = publisher;
        this.metrics = metrics;
        this.tx = tx;
        this.deduplicator = deduplicator;
        this.settings = settings;
        this.clock = clock;
    }

    /** Manual start by a user. Requires EDITOR or OWNER on the workflow. */
    public StartResult startManual(Actor actor, UUID workflowId, Integer version, JsonNode input, String idempotencyKey) {
        WorkflowAccess access = catalog.access(workflowId)
                .orElseThrow(() -> new ExecutionNotFoundException("Workflow " + workflowId));
        if (!ExecutionAccessPolicy.canView(access, actor)) {
            throw new ExecutionNotFoundException("Workflow " + workflowId);
        }
        ExecutionAccessPolicy.requireOperate(access, actor);
        if (access.archived()) {
            throw new ExecutionStateException("Archived workflows cannot be executed");
        }
        return start(new StartExecution(
                UUID.randomUUID(), workflowId, version, input, TriggerType.MANUAL, "user:" + actor.userId(), idempotencyKey, null));
    }

    /**
     * Creates an execution and starts it if a concurrency slot is free. Idempotent on {@code executionId} and on
     * (workflowId, idempotencyKey).
     */
    public StartResult start(StartExecution command) {
        return start(command, null);
    }

    /** Start requested through Kafka; {@code eventId} deduplicates redelivered requests. */
    public StartResult start(StartExecution command, UUID eventId) {
        JsonNode input = command.input() == null ? JsonNodeFactory.instance.objectNode() : command.input();
        if (!input.isObject()) {
            throw new IllegalArgumentException("Execution input must be a JSON object");
        }
        if (input.toString().getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("Execution input exceeds " + MAX_INPUT_BYTES + " bytes");
        }
        return tx.inTransaction(() -> {
            if (eventId != null && !deduplicator.firstDelivery(REQUESTS_CONSUMER, eventId)) {
                metrics.duplicateMessage(REQUESTS_CONSUMER);
                return new StartResult(command.executionId(), false);
            }
            if (executions.exists(command.executionId())) {
                return new StartResult(command.executionId(), false);
            }
            if (command.idempotencyKey() != null) {
                Optional<UUID> existing = executions.findIdByIdempotencyKey(command.workflowId(), command.idempotencyKey());
                if (existing.isPresent()) {
                    return new StartResult(existing.get(), false);
                }
            }
            PublishedVersion version = resolveVersion(command.workflowId(), command.version());
            Instant now = clock.instant();
            ExecutionGraph graph;
            try {
                graph = ExecutionGraph.parse(version.definition());
            } catch (InvalidWorkflowVersionException e) {
                throw new ExecutionStateException("Workflow version is not executable: " + e.getMessage());
            }
            WorkflowExecution execution = WorkflowExecution.create(
                    command.executionId(),
                    command.workflowId(),
                    version.version(),
                    version.name(),
                    graph,
                    command.triggerType(),
                    command.triggeredBy(),
                    input,
                    command.idempotencyKey(),
                    command.retryOf(),
                    settings,
                    now);
            boolean archived = catalog.access(command.workflowId()).map(WorkflowAccess::archived).orElse(false);
            if (archived) {
                execution.failBeforeStart(new ExecutionError("WORKFLOW_ARCHIVED", "The workflow is archived"), now);
            } else {
                tryStart(execution, now);
            }
            executions.insert(execution);
            publish(execution);
            return new StartResult(execution.id(), true);
        });
    }

    public void cancel(CancelExecution command) {
        mutate(command.executionId(), command.actor(), execution -> {
            execution.cancel(command.actor().userId(), clock.instant());
            return null;
        });
    }

    /** Retrying creates a new execution of the same version with the same input, linked to the original. */
    public StartResult retry(RetryExecution command) {
        WorkflowExecution original = executions.find(command.executionId())
                .orElseThrow(() -> new ExecutionNotFoundException("Execution " + command.executionId()));
        WorkflowAccess access = catalog.access(original.workflowId()).orElse(null);
        if (!ExecutionAccessPolicy.canView(access, command.actor())) {
            throw new ExecutionNotFoundException("Execution " + command.executionId());
        }
        ExecutionAccessPolicy.requireOperate(access, command.actor());
        if (original.status() == ExecutionStatus.COMPLETED || !original.status().isTerminal()) {
            throw new ExecutionStateException("Only failed, cancelled or timed-out executions can be retried");
        }
        return start(new StartExecution(
                UUID.randomUUID(),
                original.workflowId(),
                original.workflowVersion(),
                original.input(),
                TriggerType.RETRY,
                "user:" + command.actor().userId(),
                null,
                original.id()));
    }

    // ------------------------------------------------------------- worker results (called by Kafka consumers)

    public ResultOutcome nodeStarted(UUID eventId, UUID executionId, String nodeId, UUID attemptId, String workerId) {
        return apply(eventId, executionId, e -> {
            Instant now = clock.instant();
            ResultOutcome outcome = e.onNodeStarted(nodeId, attemptId, workerId, now);
            if (outcome == ResultOutcome.APPLIED) {
                NodeExecution node = e.node(nodeId);
                node.currentAttempt()
                        .ifPresent(a -> metrics.queueLatency(node.nodeType(), Duration.between(a.dispatchedAt(), now)));
            }
            return outcome;
        }, "started");
    }

    public ResultOutcome nodeCompleted(
            UUID eventId, UUID executionId, String nodeId, UUID attemptId, JsonNode output, String branch, String workerId) {
        return apply(eventId, executionId, e -> e.onNodeCompleted(nodeId, attemptId, output, branch, workerId, clock.instant()), "completed");
    }

    public ResultOutcome nodeFailed(
            UUID eventId, UUID executionId, String nodeId, UUID attemptId, NodeFailure failure, String workerId) {
        return apply(eventId, executionId, e -> e.onNodeFailed(nodeId, attemptId, failure, workerId, clock.instant()), "failed");
    }

    // ------------------------------------------------------------- timers (called by the sweeper)

    public void advance(UUID executionId) {
        tx.inTransaction(() -> {
            executions.loadForUpdate(executionId).ifPresent(execution -> {
                execution.advance(clock.instant());
                save(execution);
            });
            return null;
        });
    }

    public void startPending(UUID executionId) {
        tx.inTransaction(() -> {
            executions.loadForUpdate(executionId).ifPresent(execution -> {
                if (execution.status() == ExecutionStatus.PENDING && tryStart(execution, clock.instant())) {
                    save(execution);
                }
            });
            return null;
        });
    }

    // ------------------------------------------------------------- internals

    private boolean tryStart(WorkflowExecution execution, Instant now) {
        catalog.lockForScheduling(execution.workflowId());
        if (executions.countRunning(execution.workflowId()) >= execution.graph().maxConcurrentExecutions()) {
            return false;
        }
        execution.start(now);
        return true;
    }

    private PublishedVersion resolveVersion(UUID workflowId, Integer version) {
        Optional<PublishedVersion> found =
                version == null ? catalog.latestVersion(workflowId) : catalog.version(workflowId, version);
        return found.orElseThrow(() -> new VersionNotAvailableException(workflowId, version));
    }

    private ResultOutcome apply(
            UUID eventId, UUID executionId, Function<WorkflowExecution, ResultOutcome> change, String kind) {
        ResultOutcome outcome = tx.inTransaction(() -> {
            if (!deduplicator.firstDelivery(RESULTS_CONSUMER, eventId)) {
                return ResultOutcome.DUPLICATE;
            }
            Optional<WorkflowExecution> loaded = executions.loadForUpdate(executionId);
            if (loaded.isEmpty()) {
                return ResultOutcome.STALE;
            }
            WorkflowExecution execution = loaded.get();
            ResultOutcome result = change.apply(execution);
            save(execution);
            return result;
        });
        if (outcome == ResultOutcome.STALE) {
            metrics.staleResult(kind);
        } else if (outcome == ResultOutcome.DUPLICATE) {
            metrics.duplicateMessage(RESULTS_CONSUMER);
        }
        return outcome;
    }

    private void mutate(UUID executionId, Actor actor, Function<WorkflowExecution, Void> change) {
        tx.inTransaction(() -> {
            WorkflowExecution execution = executions.loadForUpdate(executionId)
                    .orElseThrow(() -> new ExecutionNotFoundException("Execution " + executionId));
            WorkflowAccess access = catalog.access(execution.workflowId()).orElse(null);
            if (!ExecutionAccessPolicy.canView(access, actor)) {
                throw new ExecutionNotFoundException("Execution " + executionId);
            }
            ExecutionAccessPolicy.requireOperate(access, actor);
            change.apply(execution);
            save(execution);
            return null;
        });
    }

    private void save(WorkflowExecution execution) {
        executions.update(execution);
        publish(execution);
    }

    private void publish(WorkflowExecution execution) {
        var effects = execution.pullEffects();
        publisher.publish(execution, effects);
        metrics.recordEffects(execution, effects);
    }

    /** The requested version has not (yet) been received from the workflow service. */
    public static class VersionNotAvailableException extends RuntimeException {
        public VersionNotAvailableException(UUID workflowId, Integer version) {
            super("Workflow " + workflowId + (version == null ? " has no published version" : " version " + version + " is not available")
                    + " yet; publication may still be propagating");
        }
    }
}
