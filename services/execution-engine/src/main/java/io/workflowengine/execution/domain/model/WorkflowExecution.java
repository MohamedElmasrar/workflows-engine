package io.workflowengine.execution.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.execution.domain.exception.ExecutionStateException;
import io.workflowengine.execution.domain.exception.IllegalTransitionException;
import io.workflowengine.execution.domain.policy.SensitiveDataMasker;
import io.workflowengine.execution.domain.service.TemplateResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root of one workflow run: the execution state machine, the node state machines and the DAG scheduling
 * rules. All methods are deterministic given {@code now}; side effects are returned as {@link ExecutionEffect}s and
 * every transition is appended to the history.
 *
 * <p>Join semantics: a node becomes READY once all incoming edges are resolved (source terminal) and at least one is
 * active (source COMPLETED and the edge matches its selected branch). Resolved with none active means SKIPPED.
 */
public final class WorkflowExecution {

    /** Persistable scalar state of the root. */
    public record State(
            UUID id,
            UUID workflowId,
            int workflowVersion,
            String workflowName,
            ExecutionStatus status,
            TriggerType triggerType,
            String triggeredBy,
            JsonNode input,
            JsonNode output,
            ExecutionError error,
            String idempotencyKey,
            UUID retryOf,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Instant deadlineAt,
            long eventSequence) {}

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final UUID id;
    private final UUID workflowId;
    private final int workflowVersion;
    private final String workflowName;
    private final TriggerType triggerType;
    private final String triggeredBy;
    private final JsonNode input;
    private final String idempotencyKey;
    private final UUID retryOf;
    private final Instant createdAt;
    private final ExecutionGraph graph;
    private final EngineSettings settings;
    private final Map<String, NodeExecution> nodes;
    private ExecutionStatus status;
    private JsonNode output;
    private ExecutionError error;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant deadlineAt;
    private long eventSequence;
    private final List<ExecutionHistoryEntry> newHistory = new ArrayList<>();
    private final List<ExecutionEffect> effects = new ArrayList<>();

    private WorkflowExecution(State s, ExecutionGraph graph, List<NodeExecution> nodeList, EngineSettings settings) {
        this.id = Objects.requireNonNull(s.id());
        this.workflowId = s.workflowId();
        this.workflowVersion = s.workflowVersion();
        this.workflowName = s.workflowName();
        this.status = s.status();
        this.triggerType = s.triggerType();
        this.triggeredBy = s.triggeredBy();
        this.input = s.input() == null ? JSON.objectNode() : s.input();
        this.output = s.output();
        this.error = s.error();
        this.idempotencyKey = s.idempotencyKey();
        this.retryOf = s.retryOf();
        this.createdAt = s.createdAt();
        this.startedAt = s.startedAt();
        this.finishedAt = s.finishedAt();
        this.deadlineAt = s.deadlineAt();
        this.eventSequence = s.eventSequence();
        this.graph = graph;
        this.settings = settings;
        this.nodes = new LinkedHashMap<>();
        nodeList.forEach(n -> nodes.put(n.nodeId(), n));
    }

    public static WorkflowExecution create(
            UUID id,
            UUID workflowId,
            int workflowVersion,
            String workflowName,
            ExecutionGraph graph,
            TriggerType triggerType,
            String triggeredBy,
            JsonNode input,
            String idempotencyKey,
            UUID retryOf,
            EngineSettings settings,
            Instant now) {
        List<NodeExecution> nodes = graph.nodes().stream()
                .map(n -> new NodeExecution(n.id(), n.type().name()))
                .toList();
        State state = new State(
                id,
                workflowId,
                workflowVersion,
                workflowName,
                ExecutionStatus.PENDING,
                triggerType,
                triggeredBy,
                input,
                null,
                null,
                idempotencyKey,
                retryOf,
                now,
                null,
                null,
                null,
                0);
        WorkflowExecution execution = new WorkflowExecution(state, graph, nodes, settings);
        ObjectNode details =
                JSON.objectNode().put("triggerType", triggerType.name()).put("workflowVersion", workflowVersion);
        execution.history(
                ExecutionHistoryEntry.EXECUTION_CREATED, null, null, ExecutionStatus.PENDING.name(), details, now);
        return execution;
    }

    public static WorkflowExecution restore(
            State state, ExecutionGraph graph, List<NodeExecution> nodes, EngineSettings settings) {
        return new WorkflowExecution(state, graph, nodes, settings);
    }

    // ---------------------------------------------------------------- lifecycle

    /** PENDING → RUNNING: completes the trigger node with the execution input and schedules its successors. */
    public void start(Instant now) {
        transition(ExecutionStatus.RUNNING, null, now);
        startedAt = now;
        deadlineAt = now.plus(graph.executionTimeout());
        NodeExecution trigger = nodes.get(graph.trigger().id());
        trigger.makeReady(now);
        recordNode(trigger, null, null, now);
        trigger.markRunning(now);
        trigger.setInput(SensitiveDataMasker.mask(input));
        recordNode(trigger, null, null, now);
        trigger.complete(input, null, now);
        recordNode(trigger, null, null, now);
        scheduleSuccessorsOf(trigger.nodeId(), now);
        dispatchReady(now);
        checkCompletion(now);
    }

    /** Used when the execution cannot start at all (e.g. the version is unusable). */
    public void failBeforeStart(ExecutionError reason, Instant now) {
        terminate(ExecutionStatus.FAILED, reason, now);
    }

    public void cancel(String requestedBy, Instant now) {
        if (status.isTerminal()) {
            throw new ExecutionStateException("Execution already finished with status " + status);
        }
        terminate(ExecutionStatus.CANCELLED, new ExecutionError("CANCELLED", "Cancelled by " + requestedBy), now);
    }

    /**
     * Processes everything that is due at {@code now}: execution deadline, expired task leases, finished delays and
     * READY nodes whose retry back-off elapsed. Safe to call repeatedly.
     */
    public void advance(Instant now) {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        if (deadlineAt != null && !now.isBefore(deadlineAt)) {
            terminate(
                    ExecutionStatus.TIMED_OUT,
                    new ExecutionError(
                            "EXECUTION_TIMEOUT",
                            "Execution exceeded " + graph.executionTimeout().toSeconds() + "s"),
                    now);
            return;
        }
        for (NodeExecution node : List.copyOf(nodes.values())) {
            if (status != ExecutionStatus.RUNNING) {
                return;
            }
            if (node.status().isActive() && node.leaseExpiresAt() != null && !now.isBefore(node.leaseExpiresAt())) {
                NodeFailure failure = NodeFailure.leaseExpired();
                node.currentAttempt().ifPresent(a -> a.finish(NodeAttempt.Status.LEASE_EXPIRED, null, now, failure));
                handleAttemptFailure(node, failure, true, now);
            } else if (node.status() == NodeStatus.RUNNING
                    && node.resumeAt() != null
                    && !now.isBefore(node.resumeAt())) {
                ObjectNode result = JSON.objectNode()
                        .put(
                                "delayedSeconds",
                                graph.node(node.nodeId())
                                        .config()
                                        .path("durationSeconds")
                                        .asLong())
                        .put("resumedAt", now.toString());
                node.complete(result, null, now);
                recordNode(node, null, null, now);
                scheduleSuccessorsOf(node.nodeId(), now);
            }
        }
        dispatchReady(now);
        checkCompletion(now);
    }

    // ---------------------------------------------------------------- worker results

    public ResultOutcome onNodeStarted(String nodeId, UUID attemptId, String workerId, Instant now) {
        NodeExecution node = nodes.get(nodeId);
        if (!acceptsResultFor(node, attemptId)) {
            return stale(nodeId, attemptId, "started", now);
        }
        if (node.status() == NodeStatus.RUNNING) {
            return ResultOutcome.DUPLICATE;
        }
        Instant lease = now.plus(graph.node(nodeId).timeout()).plus(settings.leaseGrace());
        node.markRunning(now);
        node.extendLease(lease);
        node.currentAttempt().ifPresent(a -> a.started(workerId, now, lease));
        recordNode(node, attemptId, JSON.objectNode().put("workerId", workerId), now);
        return ResultOutcome.APPLIED;
    }

    public ResultOutcome onNodeCompleted(
            String nodeId, UUID attemptId, JsonNode result, String branch, String workerId, Instant now) {
        NodeExecution node = nodes.get(nodeId);
        if (!acceptsResultFor(node, attemptId)) {
            return stale(nodeId, attemptId, "completed", now);
        }
        GraphNode definition = graph.node(nodeId);
        if (definition.type() == NodeType.CONDITION && !"true".equals(branch) && !"false".equals(branch)) {
            return onNodeFailed(
                    nodeId,
                    attemptId,
                    new NodeFailure("INVALID_BRANCH", "Condition returned no valid branch", false),
                    workerId,
                    now);
        }
        long duration = attemptDuration(node, now);
        node.currentAttempt().ifPresent(a -> a.finish(NodeAttempt.Status.COMPLETED, workerId, now, null));
        if (node.startedAt() == null) {
            node.markRunning(now);
        }
        node.complete(result == null ? JSON.nullNode() : result, branch, now);
        ObjectNode details = JSON.objectNode().put("workerId", workerId);
        if (branch != null) {
            details.put("branch", branch);
        }
        recordNode(node, attemptId, details, now);
        effects.add(new ExecutionEffect.NodeAttemptFinished(node.nodeType(), NodeStatus.COMPLETED, duration, false));
        scheduleSuccessorsOf(nodeId, now);
        dispatchReady(now);
        checkCompletion(now);
        return ResultOutcome.APPLIED;
    }

    public ResultOutcome onNodeFailed(
            String nodeId, UUID attemptId, NodeFailure failure, String workerId, Instant now) {
        NodeExecution node = nodes.get(nodeId);
        if (!acceptsResultFor(node, attemptId)) {
            return stale(nodeId, attemptId, "failed", now);
        }
        node.currentAttempt().ifPresent(a -> a.finish(NodeAttempt.Status.FAILED, workerId, now, failure));
        handleAttemptFailure(node, failure, false, now);
        return ResultOutcome.APPLIED;
    }

    // ---------------------------------------------------------------- scheduling internals

    private boolean acceptsResultFor(NodeExecution node, UUID attemptId) {
        return status == ExecutionStatus.RUNNING && node != null && node.isCurrentAttempt(attemptId);
    }

    private ResultOutcome stale(String nodeId, UUID attemptId, String kind, Instant now) {
        ObjectNode details = JSON.objectNode().put("result", kind).put("executionStatus", status.name());
        NodeExecution node = nodeId == null ? null : nodes.get(nodeId);
        if (node != null) {
            details.put("nodeStatus", node.status().name());
        }
        history(
                ExecutionHistoryEntry.STALE_RESULT_IGNORED,
                node == null ? null : nodeId,
                attemptId,
                null,
                details,
                now);
        return ResultOutcome.STALE;
    }

    private void handleAttemptFailure(NodeExecution node, NodeFailure failure, boolean leaseExpired, Instant now) {
        GraphNode definition = graph.node(node.nodeId());
        long duration = attemptDuration(node, now);
        UUID attemptId = node.currentAttemptId();
        node.recordFailure(failure);
        if (definition.retryPolicy().shouldRetry(node.attemptCount(), failure)) {
            Duration backoff = definition.retryPolicy().backoffAfter(node.attemptCount());
            Instant nextAttemptAt = now.plus(backoff);
            node.makeReady(nextAttemptAt);
            ObjectNode details = JSON.objectNode()
                    .put("failedAttempt", node.attemptCount())
                    .put("maxAttempts", definition.retryPolicy().maxAttempts())
                    .put("nextAttemptAt", nextAttemptAt.toString())
                    .put("errorCode", failure.code())
                    .put("errorMessage", failure.message());
            history(
                    ExecutionHistoryEntry.NODE_RETRY_SCHEDULED,
                    node.nodeId(),
                    attemptId,
                    NodeStatus.READY.name(),
                    details,
                    now);
            effects.add(new ExecutionEffect.NodeAttemptFinished(node.nodeType(), NodeStatus.FAILED, duration, true));
            dispatchReady(now);
            return;
        }
        NodeStatus terminal = leaseExpired ? NodeStatus.TIMED_OUT : NodeStatus.FAILED;
        node.finish(terminal, failure, now);
        recordNode(
                node,
                attemptId,
                JSON.objectNode().put("errorCode", failure.code()).put("errorMessage", failure.message()),
                now);
        effects.add(new ExecutionEffect.NodeAttemptFinished(node.nodeType(), terminal, duration, false));
        terminate(
                ExecutionStatus.FAILED,
                new ExecutionError("NODE_FAILED", "Node '" + definition.name() + "' failed: " + failure.message()),
                now);
    }

    /** Incremental scheduling: only successors of a node that just reached a terminal state are examined. */
    private void scheduleSuccessorsOf(String nodeId, Instant now) {
        Deque<String> queue = new ArrayDeque<>();
        graph.outgoing(nodeId).forEach(e -> queue.add(e.target()));
        while (!queue.isEmpty()) {
            NodeExecution candidate = nodes.get(queue.poll());
            if (candidate.status() != NodeStatus.PENDING) {
                continue;
            }
            boolean allResolved = true;
            boolean anyActive = false;
            for (GraphEdge edge : graph.incoming(candidate.nodeId())) {
                NodeExecution source = nodes.get(edge.source());
                if (!source.status().isTerminal()) {
                    allResolved = false;
                    break;
                }
                if (source.status() == NodeStatus.COMPLETED && edge.isActiveFor(source.branch())) {
                    anyActive = true;
                }
            }
            if (!allResolved) {
                continue;
            }
            if (anyActive) {
                candidate.makeReady(now);
                recordNode(candidate, null, null, now);
            } else {
                candidate.finish(NodeStatus.SKIPPED, null, now);
                recordNode(candidate, null, null, now);
                graph.outgoing(candidate.nodeId()).forEach(e -> queue.add(e.target()));
            }
        }
    }

    private void dispatchReady(Instant now) {
        if (status != ExecutionStatus.RUNNING) {
            return;
        }
        for (NodeExecution node : nodes.values()) {
            if (node.status() == NodeStatus.READY
                    && (node.availableAt() == null || !now.isBefore(node.availableAt()))) {
                dispatch(node, now);
            }
        }
    }

    private void dispatch(NodeExecution node, Instant now) {
        GraphNode definition = graph.node(node.nodeId());
        JsonNode config = TemplateResolver.resolve(definition.config(), templateContext());
        if (definition.type() == NodeType.DELAY) {
            long seconds = config.path("durationSeconds").asLong(0);
            node.setInput(config);
            node.markRunning(now);
            node.scheduleResume(now.plusSeconds(seconds));
            recordNode(
                    node,
                    null,
                    JSON.objectNode().put("resumeAt", now.plusSeconds(seconds).toString()),
                    now);
            return;
        }
        UUID attemptId = UUID.randomUUID();
        Instant lease = now.plus(definition.timeout()).plus(settings.leaseGrace());
        node.dispatch(attemptId, SensitiveDataMasker.mask(config), now, lease);
        recordNode(node, attemptId, JSON.objectNode().put("attempt", node.attemptCount()), now);
        effects.add(new ExecutionEffect.DispatchNode(new NodeTask(
                id,
                workflowId,
                node.nodeId(),
                definition.type(),
                attemptId,
                node.attemptCount(),
                definition.timeout().toMillis(),
                config)));
    }

    private TemplateResolver.Context templateContext() {
        Map<String, JsonNode> outputs = new HashMap<>();
        nodes.values().stream()
                .filter(n -> n.status() == NodeStatus.COMPLETED && n.output() != null)
                .forEach(n -> outputs.put(n.nodeId(), n.output()));
        ObjectNode execution = JSON.objectNode()
                .put("id", id.toString())
                .put("workflowId", workflowId.toString())
                .put("workflowVersion", workflowVersion);
        return new TemplateResolver.Context(input, outputs, execution);
    }

    private void checkCompletion(Instant now) {
        if (status != ExecutionStatus.RUNNING
                || !nodes.values().stream().allMatch(n -> n.status().isTerminal())) {
            return;
        }
        ObjectNode result = JSON.objectNode();
        nodes.values().stream()
                .filter(n -> n.status() == NodeStatus.COMPLETED && graph.isSink(n.nodeId()))
                .forEach(n -> result.set(n.nodeId(), n.output()));
        output = result;
        transition(ExecutionStatus.COMPLETED, null, now);
    }

    private void terminate(ExecutionStatus target, ExecutionError reason, Instant now) {
        for (NodeExecution node : nodes.values()) {
            if (node.status().isTerminal()) {
                continue;
            }
            NodeStatus nodeTarget =
                    target == ExecutionStatus.TIMED_OUT && node.status().isActive()
                            ? NodeStatus.TIMED_OUT
                            : NodeStatus.CANCELLED;
            node.currentAttempt()
                    .filter(NodeAttempt::isOpen)
                    .ifPresent(a -> a.finish(NodeAttempt.Status.CANCELLED, null, now, null));
            node.finish(nodeTarget, null, now);
            recordNode(node, null, null, now);
        }
        transition(target, reason, now);
    }

    private void transition(ExecutionStatus target, ExecutionError reason, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalTransitionException("execution " + id, status, target);
        }
        status = target;
        if (target.isTerminal()) {
            finishedAt = now;
            error = reason;
        }
        ObjectNode details = JSON.objectNode();
        if (reason != null) {
            details.put("errorCode", reason.code()).put("errorMessage", reason.message());
        }
        history(ExecutionHistoryEntry.EXECUTION_STATUS, null, null, target.name(), details, now);
        effects.add(new ExecutionEffect.LifecycleChanged(target, reason));
    }

    private void recordNode(NodeExecution node, UUID attemptId, ObjectNode details, Instant now) {
        history(
                ExecutionHistoryEntry.NODE_STATUS,
                node.nodeId(),
                attemptId,
                node.status().name(),
                details == null ? JSON.objectNode() : details,
                now);
    }

    private void history(String type, String nodeId, UUID attemptId, String newStatus, JsonNode details, Instant now) {
        newHistory.add(new ExecutionHistoryEntry(++eventSequence, type, nodeId, attemptId, newStatus, details, now));
    }

    private static long attemptDuration(NodeExecution node, Instant now) {
        return node.currentAttempt()
                .map(a -> Duration.between(a.startedAt() != null ? a.startedAt() : a.dispatchedAt(), now)
                        .toMillis())
                .orElse(0L);
    }

    // ---------------------------------------------------------------- accessors

    public List<ExecutionHistoryEntry> pullHistory() {
        List<ExecutionHistoryEntry> copy = List.copyOf(newHistory);
        newHistory.clear();
        return copy;
    }

    public List<ExecutionEffect> pullEffects() {
        List<ExecutionEffect> copy = List.copyOf(effects);
        effects.clear();
        return copy;
    }

    public State state() {
        return new State(
                id,
                workflowId,
                workflowVersion,
                workflowName,
                status,
                triggerType,
                triggeredBy,
                input,
                output,
                error,
                idempotencyKey,
                retryOf,
                createdAt,
                startedAt,
                finishedAt,
                deadlineAt,
                eventSequence);
    }

    public UUID id() {
        return id;
    }

    public UUID workflowId() {
        return workflowId;
    }

    public int workflowVersion() {
        return workflowVersion;
    }

    public ExecutionStatus status() {
        return status;
    }

    public JsonNode input() {
        return input;
    }

    public TriggerType triggerType() {
        return triggerType;
    }

    public ExecutionGraph graph() {
        return graph;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public NodeExecution node(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<NodeExecution> nodes() {
        return List.copyOf(nodes.values());
    }
}
