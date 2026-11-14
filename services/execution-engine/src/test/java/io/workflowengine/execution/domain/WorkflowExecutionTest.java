package io.workflowengine.execution.domain;

import static io.workflowengine.execution.domain.Graphs.graph;
import static io.workflowengine.execution.domain.Graphs.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.execution.domain.exception.ExecutionStateException;
import io.workflowengine.execution.domain.model.EngineSettings;
import io.workflowengine.execution.domain.model.ExecutionEffect;
import io.workflowengine.execution.domain.model.ExecutionGraph;
import io.workflowengine.execution.domain.model.ExecutionHistoryEntry;
import io.workflowengine.execution.domain.model.ExecutionStatus;
import io.workflowengine.execution.domain.model.NodeFailure;
import io.workflowengine.execution.domain.model.NodeStatus;
import io.workflowengine.execution.domain.model.NodeTask;
import io.workflowengine.execution.domain.model.ResultOutcome;
import io.workflowengine.execution.domain.model.TriggerType;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WorkflowExecutionTest {

    private static final Instant T0 = Instant.parse("2025-01-01T10:00:00Z");
    private static final EngineSettings SETTINGS = new EngineSettings(Duration.ofSeconds(10));
    private static final NodeFailure RETRYABLE = new NodeFailure("HTTP_5XX", "503", true);
    private static final NodeFailure FATAL = new NodeFailure("HTTP_4XX", "404", false);

    private WorkflowExecution execution;

    private WorkflowExecution start(ExecutionGraph graph, String input) {
        execution = WorkflowExecution.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "test",
                graph,
                TriggerType.MANUAL,
                "user",
                json(input),
                null,
                null,
                SETTINGS,
                T0);
        execution.start(T0);
        return execution;
    }

    private List<NodeTask> dispatched() {
        return execution.pullEffects().stream()
                .filter(e -> e instanceof ExecutionEffect.DispatchNode)
                .map(e -> ((ExecutionEffect.DispatchNode) e).task())
                .toList();
    }

    private NodeStatus status(String nodeId) {
        return execution.node(nodeId).status();
    }

    private UUID attempt(String nodeId) {
        return execution.node(nodeId).currentAttemptId();
    }

    private void complete(String nodeId, String output) {
        complete(nodeId, output, null);
    }

    private void complete(String nodeId, String output, String branch) {
        assertThat(execution.onNodeCompleted(nodeId, attempt(nodeId), json(output), branch, "w1", T0.plusSeconds(1)))
                .isEqualTo(ResultOutcome.APPLIED);
    }

    @Test
    void linearWorkflowRunsNodesInOrderAndCompletesWithSinkOutputs() {
        start(graph().log("a").log("b").edge("trigger", "a").edge("a", "b").build(), "{}");

        assertThat(execution.status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(status("trigger")).isEqualTo(NodeStatus.COMPLETED);
        assertThat(dispatched()).extracting(NodeTask::nodeId).containsExactly("a");
        assertThat(status("b")).isEqualTo(NodeStatus.PENDING);

        complete("a", "{\"message\":\"A\"}");
        assertThat(dispatched()).extracting(NodeTask::nodeId).containsExactly("b");
        complete("b", "{\"message\":\"B\"}");

        assertThat(execution.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(execution.state().output().get("b").get("message").asText()).isEqualTo("B");
        assertThat(execution.pullHistory())
                .extracting(ExecutionHistoryEntry::sequence)
                .isSorted()
                .doesNotHaveDuplicates();
    }

    @Test
    void independentBranchesAreDispatchedTogetherAndJoinWaitsForBoth() {
        start(
                graph().log("a")
                        .log("b")
                        .log("join")
                        .edge("trigger", "a")
                        .edge("trigger", "b")
                        .edge("a", "join")
                        .edge("b", "join")
                        .build(),
                "{}");
        assertThat(dispatched()).extracting(NodeTask::nodeId).containsExactlyInAnyOrder("a", "b");

        complete("a", "{}");
        assertThat(status("join")).isEqualTo(NodeStatus.PENDING);
        assertThat(dispatched()).isEmpty();

        complete("b", "{}");
        assertThat(dispatched()).extracting(NodeTask::nodeId).containsExactly("join");
    }

    @Test
    void conditionSelectsBranchSkipsTheOtherAndJoinStillRuns() {
        start(
                graph().node(
                                "check",
                                "CONDITION",
                                "{\"conditions\":[{\"left\":1,\"operator\":\"EQUALS\",\"right\":1}]}")
                        .log("yes")
                        .log("no")
                        .log("noFollowUp")
                        .log("join")
                        .edge("trigger", "check")
                        .edge("check", "yes", "true")
                        .edge("check", "no", "false")
                        .edge("no", "noFollowUp")
                        .edge("yes", "join")
                        .edge("noFollowUp", "join")
                        .build(),
                "{}");
        dispatched();
        complete("check", "{\"result\":true}", "true");

        assertThat(status("yes")).isEqualTo(NodeStatus.QUEUED);
        assertThat(status("no")).isEqualTo(NodeStatus.SKIPPED);
        assertThat(status("noFollowUp")).isEqualTo(NodeStatus.SKIPPED); // skip propagates
        assertThat(status("join")).isEqualTo(NodeStatus.PENDING);

        complete("yes", "{}");
        assertThat(status("join")).isEqualTo(NodeStatus.QUEUED);
        complete("join", "{}");
        assertThat(execution.status()).isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    void nodeWhoseOnlyInputsAreSkippedIsSkippedAndExecutionCompletes() {
        start(
                graph().node("check", "CONDITION", "{\"conditions\":[{\"left\":1,\"operator\":\"EXISTS\"}]}")
                        .log("onlyFalse")
                        .edge("trigger", "check")
                        .edge("check", "onlyFalse", "false")
                        .build(),
                "{}");
        complete("check", "{\"result\":true}", "true");
        assertThat(status("onlyFalse")).isEqualTo(NodeStatus.SKIPPED);
        assertThat(execution.status()).isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    void conditionWithoutValidBranchFails() {
        start(
                graph().node("check", "CONDITION", "{\"conditions\":[{\"left\":1,\"operator\":\"EXISTS\"}]}")
                        .log("x")
                        .edge("trigger", "check")
                        .edge("check", "x", "true")
                        .build(),
                "{}");
        execution.onNodeCompleted("check", attempt("check"), json("{}"), null, "w1", T0);
        assertThat(status("check")).isEqualTo(NodeStatus.FAILED);
        assertThat(execution.status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void templatesAreResolvedFromTriggerAndUpstreamOutputsAndSecretsAreMaskedInStoredInput() {
        start(
                graph().node(
                                "http",
                                "HTTP_REQUEST",
                                """
                        {"method":"POST","url":"https://api.example.com/orders/{{ trigger.orderId }}",
                         "headers":{"Authorization":"Bearer {{ trigger.token }}"},
                         "body":{"amount":"{{ trigger.amount }}","items":"{{ trigger.items }}"}}""")
                        .log("after")
                        .node("log2", "LOG", "{\"message\":\"status={{ nodes.http.output.status }}\"}")
                        .edge("trigger", "http")
                        .edge("http", "log2")
                        .build(),
                "{\"orderId\":\"o-1\",\"amount\":42.5,\"items\":[1,2],\"token\":\"s3cr3t\"}");
        NodeTask task = dispatched().get(0);
        assertThat(task.config().get("url").asText()).isEqualTo("https://api.example.com/orders/o-1");
        assertThat(task.config().at("/body/amount").isNumber()).isTrue();
        assertThat(task.config().at("/body/items").isArray()).isTrue();
        assertThat(task.config().at("/headers/Authorization").asText()).isEqualTo("Bearer s3cr3t");
        assertThat(execution.node("http").input().at("/headers/Authorization").asText())
                .doesNotContain("s3cr3t");

        complete("http", "{\"status\":201}");
        assertThat(dispatched().get(0).config().get("message").asText()).isEqualTo("status=201");
    }

    @Nested
    class Retries {

        @Test
        void retryableFailureSchedulesBackoffThenNewAttempt() {
            start(graph().log("a").retry("a", 3, 1000).edge("trigger", "a").build(), "{}");
            UUID first = dispatched().get(0).attemptId();

            execution.onNodeFailed("a", first, RETRYABLE, "w1", T0);
            assertThat(status("a")).isEqualTo(NodeStatus.READY);
            assertThat(execution.node("a").availableAt()).isEqualTo(T0.plusSeconds(1));

            execution.advance(T0.plusMillis(999));
            assertThat(dispatched()).isEmpty();

            execution.advance(T0.plusSeconds(1));
            List<NodeTask> retry = dispatched();
            assertThat(retry).singleElement().satisfies(t -> {
                assertThat(t.attemptNumber()).isEqualTo(2);
                assertThat(t.attemptId()).isNotEqualTo(first);
            });

            execution.onNodeFailed("a", retry.get(0).attemptId(), RETRYABLE, "w1", T0.plusSeconds(1));
            assertThat(execution.node("a").availableAt()).isEqualTo(T0.plusSeconds(3)); // 2s back-off
        }

        @Test
        void exhaustedRetriesFailNodeAndExecutionAndCancelTheRest() {
            start(
                    graph().log("a")
                            .log("b")
                            .log("c")
                            .retry("a", 2, 0)
                            .edge("trigger", "a")
                            .edge("trigger", "b")
                            .edge("a", "c")
                            .build(),
                    "{}");
            dispatched();
            execution.onNodeFailed("a", attempt("a"), RETRYABLE, "w1", T0);
            assertThat(status("a")).isEqualTo(NodeStatus.QUEUED); // zero back-off: re-dispatched immediately
            execution.onNodeFailed("a", attempt("a"), RETRYABLE, "w1", T0);

            assertThat(status("a")).isEqualTo(NodeStatus.FAILED);
            assertThat(status("b")).isEqualTo(NodeStatus.CANCELLED);
            assertThat(status("c")).isEqualTo(NodeStatus.CANCELLED);
            assertThat(execution.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(execution.state().error().code()).isEqualTo("NODE_FAILED");
            assertThat(execution.node("a").attempts()).hasSize(2);
        }

        @Test
        void nonRetryableFailureFailsImmediately() {
            start(graph().log("a").retry("a", 5, 0).edge("trigger", "a").build(), "{}");
            execution.onNodeFailed("a", attempt("a"), FATAL, "w1", T0);
            assertThat(status("a")).isEqualTo(NodeStatus.FAILED);
            assertThat(execution.node("a").attempts()).hasSize(1);
        }
    }

    @Nested
    class Fencing {

        @Test
        void resultOfASupersededAttemptIsIgnored() {
            start(graph().log("a").retry("a", 3, 0).edge("trigger", "a").build(), "{}");
            UUID first = attempt("a");
            execution.onNodeFailed("a", first, RETRYABLE, "w1", T0);
            UUID second = attempt("a");

            assertThat(execution.onNodeCompleted("a", first, json("{}"), null, "w1", T0))
                    .isEqualTo(ResultOutcome.STALE);
            assertThat(status("a")).isEqualTo(NodeStatus.QUEUED);
            assertThat(execution.onNodeCompleted("a", second, json("{}"), null, "w1", T0))
                    .isEqualTo(ResultOutcome.APPLIED);
        }

        @Test
        void duplicateCompletionAfterSuccessIsIgnored() {
            start(graph().log("a").edge("trigger", "a").build(), "{}");
            UUID a = attempt("a");
            execution.onNodeCompleted("a", a, json("{\"n\":1}"), null, "w1", T0);
            assertThat(execution.onNodeCompleted("a", a, json("{\"n\":2}"), null, "w1", T0))
                    .isEqualTo(ResultOutcome.STALE);
            assertThat(execution.node("a").output().get("n").asInt()).isEqualTo(1);
        }

        @Test
        void duplicateStartedIsReportedAsDuplicate() {
            start(graph().log("a").edge("trigger", "a").build(), "{}");
            assertThat(execution.onNodeStarted("a", attempt("a"), "w1", T0)).isEqualTo(ResultOutcome.APPLIED);
            assertThat(execution.onNodeStarted("a", attempt("a"), "w1", T0)).isEqualTo(ResultOutcome.DUPLICATE);
        }

        @Test
        void unknownNodeIsStale() {
            start(graph().log("a").edge("trigger", "a").build(), "{}");
            assertThat(execution.onNodeCompleted("ghost", UUID.randomUUID(), json("{}"), null, "w", T0))
                    .isEqualTo(ResultOutcome.STALE);
        }
    }

    @Nested
    class Leases {

        @Test
        void expiredLeaseStartsANewAttempt() {
            start(
                    graph().log("a")
                            .timeout("a", 5)
                            .retry("a", 2, 0)
                            .edge("trigger", "a")
                            .build(),
                    "{}");
            UUID first = attempt("a");
            assertThat(execution.node("a").leaseExpiresAt()).isEqualTo(T0.plusSeconds(15)); // timeout + grace

            execution.advance(T0.plusSeconds(14));
            assertThat(attempt("a")).isEqualTo(first);

            execution.advance(T0.plusSeconds(15));
            assertThat(attempt("a")).isNotEqualTo(first);
            assertThat(execution.node("a").attempts().get(0).status().name()).isEqualTo("LEASE_EXPIRED");
        }

        @Test
        void startedEventExtendsTheLease() {
            start(graph().log("a").timeout("a", 5).edge("trigger", "a").build(), "{}");
            execution.onNodeStarted("a", attempt("a"), "w1", T0.plusSeconds(12));
            assertThat(execution.node("a").leaseExpiresAt()).isEqualTo(T0.plusSeconds(27));
        }

        @Test
        void leaseExpiryWithoutAttemptsLeftTimesOutTheNode() {
            start(
                    graph().log("a")
                            .timeout("a", 1)
                            .retry("a", 1, 0)
                            .edge("trigger", "a")
                            .build(),
                    "{}");
            execution.advance(T0.plusSeconds(11));
            assertThat(status("a")).isEqualTo(NodeStatus.TIMED_OUT);
            assertThat(execution.status()).isEqualTo(ExecutionStatus.FAILED);
        }
    }

    @Test
    void executionDeadlineTimesOutActiveNodes() {
        start(
                graph().log("a")
                        .log("b")
                        .timeout("a", 300)
                        .edge("trigger", "a")
                        .edge("a", "b")
                        .executionTimeout(60)
                        .build(),
                "{}");
        execution.advance(T0.plusSeconds(60));
        assertThat(execution.status()).isEqualTo(ExecutionStatus.TIMED_OUT);
        assertThat(status("a")).isEqualTo(NodeStatus.TIMED_OUT);
        assertThat(status("b")).isEqualTo(NodeStatus.CANCELLED);
    }

    @Test
    void cancellationStopsSchedulingAndLateResultsAreIgnored() {
        start(graph().log("a").log("b").edge("trigger", "a").edge("a", "b").build(), "{}");
        UUID a = attempt("a");
        execution.cancel("alice", T0);
        assertThat(execution.status()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(status("a")).isEqualTo(NodeStatus.CANCELLED);
        assertThat(execution.onNodeCompleted("a", a, json("{}"), null, "w1", T0))
                .isEqualTo(ResultOutcome.STALE);
        assertThat(status("b")).isEqualTo(NodeStatus.CANCELLED);
        assertThatThrownBy(() -> execution.cancel("alice", T0)).isInstanceOf(ExecutionStateException.class);
    }

    @Test
    void pendingExecutionCanBeCancelled() {
        execution = WorkflowExecution.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "t",
                graph().log("a").edge("trigger", "a").build(),
                TriggerType.MANUAL,
                "u",
                json("{}"),
                null,
                null,
                SETTINGS,
                T0);
        execution.cancel("u", T0);
        assertThat(execution.status()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(status("trigger")).isEqualTo(NodeStatus.CANCELLED);
    }

    @Test
    void delayNodeIsADurableTimerHandledByTheEngine() {
        start(
                graph().node("wait", "DELAY", "{\"durationSeconds\":30}")
                        .log("after")
                        .edge("trigger", "wait")
                        .edge("wait", "after")
                        .build(),
                "{}");
        assertThat(dispatched()).isEmpty();
        assertThat(status("wait")).isEqualTo(NodeStatus.RUNNING);
        assertThat(execution.node("wait").resumeAt()).isEqualTo(T0.plusSeconds(30));

        execution.advance(T0.plusSeconds(29));
        assertThat(status("wait")).isEqualTo(NodeStatus.RUNNING);
        execution.advance(T0.plusSeconds(30));
        assertThat(status("wait")).isEqualTo(NodeStatus.COMPLETED);
        assertThat(dispatched()).extracting(NodeTask::nodeId).containsExactly("after");
    }

    @Test
    void lifecycleEffectsAreEmittedForStartAndTerminalStates() {
        start(graph().log("a").edge("trigger", "a").build(), "{}");
        complete("a", "{}");
        List<ExecutionStatus> lifecycle = execution.pullEffects().stream()
                .filter(e -> e instanceof ExecutionEffect.LifecycleChanged)
                .map(e -> ((ExecutionEffect.LifecycleChanged) e).status())
                .toList();
        assertThat(lifecycle).containsExactly(ExecutionStatus.RUNNING, ExecutionStatus.COMPLETED);
    }

    @Test
    void historyNeverContainsNodeOutputs() {
        start(graph().log("a").edge("trigger", "a").build(), "{\"password\":\"hunter2\"}");
        complete("a", "{\"secret\":\"value\"}");
        assertThat(execution.pullHistory())
                .extracting(h -> h.details() == null ? "" : h.details().toString())
                .noneMatch(d -> d.contains("hunter2") || d.contains("value"));
    }

    @SuppressWarnings("unused")
    private static JsonNode unused() {
        return null;
    }
}
