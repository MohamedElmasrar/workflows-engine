package io.workflowengine.execution.infrastructure;

import static io.workflowengine.execution.domain.Graphs.graph;
import static io.workflowengine.execution.domain.Graphs.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.NodeError;
import io.workflowengine.contracts.execution.NodeExecutionCompleted;
import io.workflowengine.contracts.execution.NodeExecutionFailed;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.contracts.execution.NodeExecutionStarted;
import io.workflowengine.contracts.execution.WorkflowExecutionRequested;
import io.workflowengine.execution.application.usecase.ExecutionService;
import io.workflowengine.execution.application.usecase.StartResult;
import io.workflowengine.execution.domain.model.Actor;
import io.workflowengine.execution.domain.model.NodeFailure;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExecutionFlowIT extends EngineTestSupport {

    @Autowired
    ExecutionService service;

    private final Actor alice = new Actor("alice", false);

    private void completeViaKafka(NodeExecutionScheduled task, String output, String branch) {
        String key = task.executionId().toString();
        send(
                Topics.NODE_RESULTS,
                EventTypes.NODE_EXECUTION_STARTED,
                key,
                new NodeExecutionStarted(
                        task.executionId(), task.nodeId(), task.attemptId(), "test-worker", Instant.now()));
        send(
                Topics.NODE_RESULTS,
                EventTypes.NODE_EXECUTION_COMPLETED,
                key,
                new NodeExecutionCompleted(
                        task.executionId(),
                        task.nodeId(),
                        task.attemptId(),
                        "test-worker",
                        json(output),
                        branch,
                        5,
                        Instant.now()));
    }

    @Test
    void manualExecutionRunsEndToEndThroughKafkaWithBranching() {
        UUID workflowId = publishWorkflow(
                "alice",
                graph().node(
                                "check",
                                "CONDITION",
                                "{\"conditions\":[{\"left\":\"{{ trigger.amount }}\",\"operator\":\"GREATER_THAN\",\"right\":10}]}")
                        .log("big")
                        .log("small")
                        .edge("trigger", "check")
                        .edge("check", "big", "true")
                        .edge("check", "small", "false")
                        .json());

        StartResult started = service.startManual(alice, workflowId, null, json("{\"amount\":50}"), null);
        UUID id = started.executionId();

        NodeExecutionScheduled check = awaitTask(id, "check", 1);
        assertThat(check.config().at("/conditions/0/left").asInt()).isEqualTo(50); // template resolved
        completeViaKafka(check, "{\"result\":true}", "true");

        NodeExecutionScheduled big = awaitTask(id, "big", 1);
        completeViaKafka(big, "{\"message\":\"big\"}", null);

        awaitExecutionStatus(id, "COMPLETED");
        assertThat(nodeStatus(id, "small")).isEqualTo("SKIPPED");
        assertThat(nodeStatus(id, "big")).isEqualTo("COMPLETED");
        // lifecycle events were published through the outbox
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from outbox_event where message_key = ? and topic = ? and published_at is not null",
                                Long.class,
                                id.toString(),
                                Topics.EXECUTION_LIFECYCLE)
                        == 2);
    }

    @Test
    void duplicateAndStaleResultMessagesDoNotCauseDuplicateTransitions() {
        UUID workflowId = publishWorkflow(
                "alice",
                graph().log("a").log("b").edge("trigger", "a").edge("a", "b").json());
        UUID id = service.startManual(alice, workflowId, null, json("{}"), null).executionId();
        NodeExecutionScheduled a = awaitTask(id, "a", 1);

        EventEnvelope completed = codec.envelope(
                EventTypes.NODE_EXECUTION_COMPLETED,
                id.toString(),
                new NodeExecutionCompleted(id, "a", a.attemptId(), "w", json("{}"), null, 1, Instant.now()),
                "c",
                null);
        sendRaw(Topics.NODE_RESULTS, id.toString(), completed);
        sendRaw(Topics.NODE_RESULTS, id.toString(), completed); // redelivery: same event id
        send(
                Topics.NODE_RESULTS,
                EventTypes.NODE_EXECUTION_COMPLETED,
                id.toString(), // different event, same attempt
                new NodeExecutionCompleted(
                        id, "a", a.attemptId(), "w", json("{\"again\":true}"), null, 1, Instant.now()));

        awaitTask(id, "b", 1);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from execution_event where execution_id = ? and event_type = 'STALE_RESULT_IGNORED'",
                                Long.class,
                                id)
                        == 1);
        assertThat(historyCount(id, "a", "COMPLETED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from processed_message where event_id = ?", Long.class, completed.eventId()))
                .isEqualTo(1);
        assertThat(tasks.stream()
                        .filter(t -> codec.payload(t, NodeExecutionScheduled.class)
                                        .nodeId()
                                        .equals("b")
                                && codec.payload(t, NodeExecutionScheduled.class)
                                        .executionId()
                                        .equals(id)))
                .hasSize(1);
    }

    @Test
    void concurrentCompletionsOfParallelNodesAreAllApplied() throws Exception {
        UUID workflowId = publishWorkflow(
                "alice",
                graph().log("a")
                        .log("b")
                        .log("c")
                        .log("d")
                        .log("join")
                        .edge("trigger", "a")
                        .edge("trigger", "b")
                        .edge("trigger", "c")
                        .edge("trigger", "d")
                        .edge("a", "join")
                        .edge("b", "join")
                        .edge("c", "join")
                        .edge("d", "join")
                        .json());
        UUID id = service.startManual(alice, workflowId, null, json("{}"), null).executionId();
        List<NodeExecutionScheduled> parallel = List.of("a", "b", "c", "d").stream()
                .map(n -> awaitTask(id, n, 1))
                .toList();

        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = parallel.stream()
                .map(t -> CompletableFuture.runAsync(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    service.nodeCompleted(UUID.randomUUID(), id, t.nodeId(), t.attemptId(), json("{}"), null, "w");
                }))
                .toList();
        go.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        for (String n : List.of("a", "b", "c", "d")) {
            assertThat(nodeStatus(id, n)).isEqualTo("COMPLETED");
        }
        NodeExecutionScheduled join = awaitTask(id, "join", 1);
        assertThat(tasks.stream()
                        .map(e -> codec.payload(e, NodeExecutionScheduled.class))
                        .filter(t -> t.executionId().equals(id) && t.nodeId().equals("join")))
                .hasSize(1);
        service.nodeCompleted(UUID.randomUUID(), id, "join", join.attemptId(), json("{}"), null, "w");
        assertThat(executionStatus(id)).isEqualTo("COMPLETED");
        // event sequence numbers are gap-free and unique despite the concurrent writers
        Long max =
                jdbc.queryForObject("select max(sequence) from execution_event where execution_id = ?", Long.class, id);
        Long count = jdbc.queryForObject("select count(*) from execution_event where execution_id = ?", Long.class, id);
        assertThat(max).isEqualTo(count);
    }

    @Test
    void lostTaskIsRecoveredAfterLeaseExpiryAndOldAttemptIsFenced() {
        UUID workflowId = publishWorkflow(
                "alice",
                graph().log("a")
                        .timeout("a", 1)
                        .retry("a", 3, 100)
                        .edge("trigger", "a")
                        .json());
        UUID id = service.startManual(alice, workflowId, null, json("{}"), null).executionId();
        NodeExecutionScheduled first = awaitTask(id, "a", 1);

        // nobody answers: lease = 1s timeout + 2s grace; the sweeper dispatches attempt 2
        NodeExecutionScheduled second = awaitTask(id, "a", 2);
        assertThat(second.attemptId()).isNotEqualTo(first.attemptId());
        assertThat(jdbc.queryForObject(
                        "select status from node_attempt where attempt_id = ?", String.class, first.attemptId()))
                .isEqualTo("LEASE_EXPIRED");

        // the slow first worker finally answers: ignored
        service.nodeCompleted(UUID.randomUUID(), id, "a", first.attemptId(), json("{\"late\":true}"), null, "slow");
        assertThat(nodeStatus(id, "a")).isEqualTo("QUEUED");

        service.nodeCompleted(UUID.randomUUID(), id, "a", second.attemptId(), json("{}"), null, "w");
        assertThat(executionStatus(id)).isEqualTo("COMPLETED");
    }

    @Test
    void retryableFailuresAreRetriedWithBackoffThenFail() {
        UUID workflowId = publishWorkflow(
                "alice",
                graph().log("a").retry("a", 2, 300).edge("trigger", "a").json());
        UUID id = service.startManual(alice, workflowId, null, json("{}"), null).executionId();
        NodeExecutionScheduled first = awaitTask(id, "a", 1);
        send(
                Topics.NODE_RESULTS,
                EventTypes.NODE_EXECUTION_FAILED,
                id.toString(),
                new NodeExecutionFailed(
                        id, "a", first.attemptId(), "w", new NodeError("HTTP_5XX", "503", true), 1, Instant.now()));
        NodeExecutionScheduled second = awaitTask(id, "a", 2);
        send(
                Topics.NODE_RESULTS,
                EventTypes.NODE_EXECUTION_FAILED,
                id.toString(),
                new NodeExecutionFailed(
                        id, "a", second.attemptId(), "w", new NodeError("HTTP_5XX", "503", true), 1, Instant.now()));
        awaitExecutionStatus(id, "FAILED");
        assertThat(nodeStatus(id, "a")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from node_attempt where execution_id = ?", Long.class, id))
                .isEqualTo(2);
    }

    @Test
    void cancellationRacingWithCompletionLeavesAValidTerminalState() throws Exception {
        UUID workflowId = publishWorkflow(
                "alice",
                graph().log("a").log("b").edge("trigger", "a").edge("a", "b").json());
        for (int i = 0; i < 5; i++) {
            UUID id = service.startManual(alice, workflowId, null, json("{}"), null)
                    .executionId();
            NodeExecutionScheduled a = awaitTask(id, "a", 1);
            CountDownLatch go = new CountDownLatch(1);
            CompletableFuture<?> cancel = CompletableFuture.runAsync(() -> {
                waitFor(go);
                service.cancel(new io.workflowengine.execution.application.command.ExecutionCommands.CancelExecution(
                        alice, id));
            });
            CompletableFuture<?> complete = CompletableFuture.runAsync(() -> {
                waitFor(go);
                service.nodeCompleted(UUID.randomUUID(), id, "a", a.attemptId(), json("{}"), null, "w");
            });
            go.countDown();
            CompletableFuture.allOf(cancel, complete).join();

            assertThat(executionStatus(id)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForList("select status from node_execution where execution_id = ?", String.class, id))
                    .allMatch(s -> List.of("COMPLETED", "CANCELLED").contains(s));
        }
    }

    @Test
    void delayNodeResumesFromPersistedTimer() {
        UUID workflowId = publishWorkflow(
                "alice",
                graph().node("wait", "DELAY", "{\"durationSeconds\":1}")
                        .log("after")
                        .edge("trigger", "wait")
                        .edge("wait", "after")
                        .json());
        UUID id = service.startManual(alice, workflowId, null, json("{}"), null).executionId();
        assertThat(nodeStatus(id, "wait")).isEqualTo("RUNNING");
        awaitTask(id, "after", 1);
        assertThat(nodeStatus(id, "wait")).isEqualTo("COMPLETED");
    }

    @Test
    void executionRequestsAreIdempotentAndWaitForTheVersionProjection() {
        UUID workflowId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        WorkflowExecutionRequested request = new WorkflowExecutionRequested(
                executionId, workflowId, 1, "WEBHOOK", "webhook:test", json("{\"x\":1}"), "delivery-1");
        // the request arrives before the workflow version projection
        send(Topics.EXECUTION_REQUESTS, EventTypes.WORKFLOW_EXECUTION_REQUESTED, workflowId.toString(), request);
        await().pollDelay(Duration.ofMillis(500)).until(() -> true);

        var definition = graph().log("a").edge("trigger", "a").json();
        send(
                Topics.WORKFLOW_EVENTS,
                EventTypes.WORKFLOW_ACCESS_UPDATED,
                workflowId.toString(),
                new io.workflowengine.contracts.workflow.WorkflowAccessUpdated(
                        workflowId, "alice", 1, List.of(), false));
        send(
                Topics.WORKFLOW_EVENTS,
                EventTypes.WORKFLOW_VERSION_PUBLISHED,
                workflowId.toString(),
                new io.workflowengine.contracts.workflow.WorkflowVersionPublished(
                        workflowId, 1, "late", "alice", "alice", Instant.now(), definition));
        awaitTask(executionId, "a", 1);

        // duplicate request (new event id, same execution id) does not create a second execution
        send(Topics.EXECUTION_REQUESTS, EventTypes.WORKFLOW_EXECUTION_REQUESTED, workflowId.toString(), request);
        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);
        assertThat(jdbc.queryForObject(
                        "select count(*) from workflow_execution where workflow_id = ?", Long.class, workflowId))
                .isEqualTo(1);
    }

    @Test
    void malformedMessagesGoToTheDeadLetterTopic() {
        kafka.send(Topics.NODE_RESULTS, "k", "{not json").join();
        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(java.util.Map.of(
                "bootstrap.servers",
                String.join(",", kafkaConnection.getBootstrapServers()),
                "group.id",
                "dlt-" + UUID.randomUUID(),
                "auto.offset.reset",
                "earliest",
                "key.deserializer",
                org.apache.kafka.common.serialization.StringDeserializer.class,
                "value.deserializer",
                org.apache.kafka.common.serialization.StringDeserializer.class))) {
            consumer.subscribe(List.of(Topics.deadLetter(Topics.NODE_RESULTS)));
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (var r : consumer.poll(Duration.ofMillis(500))) {
                    if (r.value().equals("{not json")) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    @Test
    void failureOfANodeIsRecordedWithAttemptHistory() {
        UUID workflowId = publishWorkflow(
                "alice", graph().log("a").retry("a", 1, 0).edge("trigger", "a").json());
        UUID id = service.startManual(alice, workflowId, null, json("{}"), null).executionId();
        NodeExecutionScheduled a = awaitTask(id, "a", 1);
        service.nodeFailed(
                UUID.randomUUID(), id, "a", a.attemptId(), new NodeFailure("HTTP_4XX", "Not found", false), "w");
        assertThat(executionStatus(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                        "select error_code from node_attempt where attempt_id = ?", String.class, a.attemptId()))
                .isEqualTo("HTTP_4XX");
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
