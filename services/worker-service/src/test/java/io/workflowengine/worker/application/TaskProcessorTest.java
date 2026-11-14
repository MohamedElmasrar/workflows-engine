package io.workflowengine.worker.application;

import static io.workflowengine.worker.domain.ConditionHandlerTest.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.contracts.execution.NodeExecutionFailed;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.worker.application.port.out.AttemptCache;
import io.workflowengine.worker.application.port.out.WorkerMetrics;
import io.workflowengine.worker.application.usecase.TaskProcessor;
import io.workflowengine.worker.domain.handler.NodeHandler;
import io.workflowengine.worker.domain.handler.NodeHandlerRegistry;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskProcessorTest {

    record Published(String type, Object payload) {}

    private final List<Published> published = new ArrayList<>();
    private final Map<UUID, AttemptCache.CachedResult> cache = new HashMap<>();
    private final AtomicInteger executions = new AtomicInteger();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private TaskProcessor processor(NodeHandler handler) {
        AttemptCache attemptCache = new AttemptCache() {
            public Optional<CachedResult> finished(UUID id) {
                return Optional.ofNullable(cache.get(id));
            }

            public void storeFinished(UUID id, CachedResult r) {
                cache.put(id, r);
            }
        };
        WorkerMetrics metrics = new WorkerMetrics() {
            public void taskProcessed(String t, String o, Duration d) {}

            public void cachedResultReplayed(String t) {}
        };
        return new TaskProcessor(new NodeHandlerRegistry(List.of(handler)), (type, key, payload) -> published.add(new Published(type, payload)),
                attemptCache, metrics, Executors.newVirtualThreadPerTaskExecutor(), json, Clock.systemUTC(), "w1");
    }

    private static NodeHandler handler(java.util.function.Function<NodeExecutionContext, NodeResult> body) {
        return new NodeHandler() {
            public NodeType supportedType() {
                return NodeType.LOG;
            }

            public NodeResult execute(NodeExecutionContext c, com.fasterxml.jackson.databind.JsonNode config) {
                return body.apply(c);
            }
        };
    }

    private static NodeExecutionScheduled task(UUID attemptId, long timeoutMs) {
        return new NodeExecutionScheduled(UUID.randomUUID(), UUID.randomUUID(), "n", "LOG", attemptId, 1, timeoutMs, json("{}"));
    }

    @Test
    void reportsStartedThenCompletedAndCachesTheResult() {
        TaskProcessor p = processor(handler(c -> {
            executions.incrementAndGet();
            return NodeResult.success(JsonNodeFactory.instance.objectNode().put("ok", true));
        }));
        NodeExecutionScheduled t = task(UUID.randomUUID(), 1000);
        p.process(t);
        assertThat(published).extracting(Published::type)
                .containsExactly(EventTypes.NODE_EXECUTION_STARTED, EventTypes.NODE_EXECUTION_COMPLETED);
        assertThat(cache).containsKey(t.attemptId());

        published.clear();
        p.process(t); // redelivery of the same attempt
        assertThat(executions).hasValue(1);
        assertThat(published).extracting(Published::type).containsExactly(EventTypes.NODE_EXECUTION_COMPLETED);
    }

    @Test
    void timeoutsAreRetryableFailures() {
        TaskProcessor p = processor(handler(c -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return NodeResult.success(null);
        }));
        p.process(task(UUID.randomUUID(), 100));
        NodeExecutionFailed failed = (NodeExecutionFailed) published.get(1).payload();
        assertThat(failed.error().code()).isEqualTo("TIMEOUT");
        assertThat(failed.error().retryable()).isTrue();
    }

    @Test
    void classifiedExceptionsBecomeFailuresAndUnexpectedOnesAreRetryable() {
        processor(handler(c -> { throw new NodeExecutionException("HTTP_4XX", "nope", false); }))
                .process(task(UUID.randomUUID(), 1000));
        processor(handler(c -> { throw new IllegalStateException("bug"); })).process(task(UUID.randomUUID(), 1000));
        assertThat(((NodeExecutionFailed) published.get(1).payload()).error().retryable()).isFalse();
        assertThat(((NodeExecutionFailed) published.get(3).payload()).error().code()).isEqualTo("HANDLER_ERROR");
    }

    @Test
    void oversizedOutputIsRejected() {
        String big = "x".repeat(TaskProcessor.MAX_OUTPUT_BYTES + 1);
        processor(handler(c -> NodeResult.success(JsonNodeFactory.instance.textNode(big)))).process(task(UUID.randomUUID(), 2000));
        assertThat(((NodeExecutionFailed) published.get(1).payload()).error().code()).isEqualTo("OUTPUT_TOO_LARGE");
    }

    @Test
    void unsupportedTypesFailWithoutRetry() {
        TaskProcessor p = processor(handler(c -> NodeResult.success(null)));
        p.process(new NodeExecutionScheduled(UUID.randomUUID(), UUID.randomUUID(), "n", "DELAY", UUID.randomUUID(), 1, 1000, json("{}")));
        assertThat(((NodeExecutionFailed) published.get(1).payload()).error().code()).isEqualTo("UNSUPPORTED_NODE_TYPE");
    }

    @Test
    void registryRejectsDuplicateHandlers() {
        NodeHandler h = handler(c -> null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new NodeHandlerRegistry(List.of(h, h)))
                .isInstanceOf(IllegalStateException.class);
    }
}
