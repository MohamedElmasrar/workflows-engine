package io.workflowengine.worker.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.contracts.execution.NodeError;
import io.workflowengine.contracts.execution.NodeExecutionCompleted;
import io.workflowengine.contracts.execution.NodeExecutionFailed;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.contracts.execution.NodeExecutionStarted;
import io.workflowengine.worker.application.port.out.AttemptCache;
import io.workflowengine.worker.application.port.out.ResultPublisher;
import io.workflowengine.worker.application.port.out.WorkerMetrics;
import io.workflowengine.worker.domain.handler.NodeHandler;
import io.workflowengine.worker.domain.handler.NodeHandlerRegistry;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes one task: replay a cached result if this attempt already finished, otherwise report Started, run the
 * handler under the node timeout, report the result and cache it. The Kafka offset is committed only after this
 * method returns, so a crash at any point causes redelivery (at-least-once).
 */
public class TaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(TaskProcessor.class);
    public static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private final NodeHandlerRegistry registry;
    private final ResultPublisher publisher;
    private final AttemptCache cache;
    private final WorkerMetrics metrics;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String workerId;

    public TaskProcessor(
            NodeHandlerRegistry registry,
            ResultPublisher publisher,
            AttemptCache cache,
            WorkerMetrics metrics,
            ExecutorService executor,
            ObjectMapper objectMapper,
            Clock clock,
            String workerId) {
        this.registry = registry;
        this.publisher = publisher;
        this.cache = cache;
        this.metrics = metrics;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.workerId = workerId;
    }

    public void process(NodeExecutionScheduled task) {
        String key = task.executionId().toString();
        Optional<AttemptCache.CachedResult> cached = cache.finished(task.attemptId());
        if (cached.isPresent()) {
            log.info(
                    "Attempt {} of node {} already finished; re-publishing cached result",
                    task.attemptId(),
                    task.nodeId());
            publisher.publish(
                    cached.get().eventType(), key, readTree(cached.get().payloadJson()));
            metrics.cachedResultReplayed(task.nodeType());
            return;
        }

        Instant started = clock.instant();
        publisher.publish(
                EventTypes.NODE_EXECUTION_STARTED,
                key,
                new NodeExecutionStarted(task.executionId(), task.nodeId(), task.attemptId(), workerId, started));

        NodeResult result = execute(task);
        long durationMs = Duration.between(started, clock.instant()).toMillis();
        String eventType;
        Object payload;
        String outcome;
        if (result instanceof NodeResult.Success success) {
            eventType = EventTypes.NODE_EXECUTION_COMPLETED;
            payload = new NodeExecutionCompleted(
                    task.executionId(),
                    task.nodeId(),
                    task.attemptId(),
                    workerId,
                    success.output(),
                    success.branch(),
                    durationMs,
                    clock.instant());
            outcome = "COMPLETED";
        } else {
            NodeResult.Failure failure = (NodeResult.Failure) result;
            eventType = EventTypes.NODE_EXECUTION_FAILED;
            payload = new NodeExecutionFailed(
                    task.executionId(),
                    task.nodeId(),
                    task.attemptId(),
                    workerId,
                    new NodeError(failure.code(), failure.message(), failure.retryable()),
                    durationMs,
                    clock.instant());
            outcome = "FAILED";
            log.info(
                    "Node {} attempt {} failed: {} ({})",
                    task.nodeId(),
                    task.attemptNumber(),
                    failure.code(),
                    failure.message());
        }
        publisher.publish(eventType, key, payload);
        cache.storeFinished(task.attemptId(), new AttemptCache.CachedResult(eventType, writeJson(payload)));
        metrics.taskProcessed(task.nodeType(), outcome, Duration.ofMillis(durationMs));
    }

    private NodeResult execute(NodeExecutionScheduled task) {
        NodeType type;
        try {
            type = NodeType.valueOf(task.nodeType());
        } catch (IllegalArgumentException e) {
            return new NodeResult.Failure("UNSUPPORTED_NODE_TYPE", "Unknown node type " + task.nodeType(), false);
        }
        Optional<NodeHandler> handler = registry.resolve(type);
        if (handler.isEmpty()) {
            return new NodeResult.Failure("UNSUPPORTED_NODE_TYPE", "No worker handler for " + type, false);
        }
        Duration timeout = Duration.ofMillis(task.timeoutMs());
        NodeExecutionContext context = new NodeExecutionContext(
                task.executionId(),
                task.workflowId(),
                task.nodeId(),
                type,
                task.attemptId(),
                task.attemptNumber(),
                timeout);
        Future<NodeResult> future = executor.submit(() -> handler.get().execute(context, task.config()));
        try {
            NodeResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return enforceOutputLimit(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new NodeResult.Failure("TIMEOUT", "Node exceeded its timeout of " + timeout.toSeconds() + "s", true);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NodeExecutionException n) {
                return n.toFailure();
            }
            log.error("Handler for {} failed unexpectedly", type, cause);
            return new NodeResult.Failure(
                    "HANDLER_ERROR", "Unexpected error: " + cause.getClass().getSimpleName(), true);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted while executing node", e);
        }
    }

    private NodeResult enforceOutputLimit(NodeResult result) {
        if (result instanceof NodeResult.Success success
                && success.output() != null
                && writeJson(success.output()).getBytes(StandardCharsets.UTF_8).length > MAX_OUTPUT_BYTES) {
            return new NodeResult.Failure(
                    "OUTPUT_TOO_LARGE", "Node output exceeds " + MAX_OUTPUT_BYTES + " bytes", false);
        }
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
