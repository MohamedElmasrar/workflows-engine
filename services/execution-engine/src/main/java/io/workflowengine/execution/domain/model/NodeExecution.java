package io.workflowengine.execution.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.execution.domain.exception.IllegalTransitionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Runtime state of one node inside a {@link WorkflowExecution}. Mutated only through the aggregate. */
public final class NodeExecution {

    private final String nodeId;
    private final String nodeType;
    private NodeStatus status;
    private int attemptCount;
    private UUID currentAttemptId;
    private Instant availableAt;
    private Instant leaseExpiresAt;
    private Instant resumeAt;
    private JsonNode input;
    private JsonNode output;
    private String branch;
    private NodeFailure error;
    private Instant startedAt;
    private Instant finishedAt;
    private final List<NodeAttempt> attempts;

    public NodeExecution(String nodeId, String nodeType) {
        this(
                nodeId,
                nodeType,
                NodeStatus.PENDING,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of());
    }

    public NodeExecution(
            String nodeId,
            String nodeType,
            NodeStatus status,
            int attemptCount,
            UUID currentAttemptId,
            Instant availableAt,
            Instant leaseExpiresAt,
            Instant resumeAt,
            JsonNode input,
            JsonNode output,
            String branch,
            NodeFailure error,
            Instant startedAt,
            Instant finishedAt,
            List<NodeAttempt> attempts) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.status = status;
        this.attemptCount = attemptCount;
        this.currentAttemptId = currentAttemptId;
        this.availableAt = availableAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.resumeAt = resumeAt;
        this.input = input;
        this.output = output;
        this.branch = branch;
        this.error = error;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.attempts = new ArrayList<>(attempts);
    }

    void transitionTo(NodeStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalTransitionException("node " + nodeId, status, target);
        }
        status = target;
    }

    void makeReady(Instant at) {
        transitionTo(NodeStatus.READY);
        availableAt = at;
        leaseExpiresAt = null;
        currentAttemptId = null;
    }

    NodeAttempt dispatch(UUID attemptId, JsonNode maskedInput, Instant now, Instant lease) {
        transitionTo(NodeStatus.QUEUED);
        attemptCount++;
        currentAttemptId = attemptId;
        leaseExpiresAt = lease;
        availableAt = null;
        input = maskedInput;
        NodeAttempt attempt = new NodeAttempt(attemptId, attemptCount, now, lease);
        attempts.add(attempt);
        return attempt;
    }

    void markRunning(Instant at) {
        transitionTo(NodeStatus.RUNNING);
        if (startedAt == null) {
            startedAt = at;
        }
    }

    void complete(JsonNode result, String selectedBranch, Instant at) {
        transitionTo(NodeStatus.COMPLETED);
        output = result;
        branch = selectedBranch;
        error = null;
        finishedAt = at;
        clearTimers();
    }

    void finish(NodeStatus terminal, NodeFailure failure, Instant at) {
        transitionTo(terminal);
        if (failure != null) {
            error = failure;
        }
        finishedAt = at;
        clearTimers();
    }

    void scheduleResume(Instant at) {
        resumeAt = at;
    }

    void recordFailure(NodeFailure failure) {
        error = failure;
    }

    void setInput(JsonNode value) {
        input = value;
    }

    void extendLease(Instant lease) {
        leaseExpiresAt = lease;
    }

    private void clearTimers() {
        availableAt = null;
        leaseExpiresAt = null;
        resumeAt = null;
    }

    public Optional<NodeAttempt> currentAttempt() {
        if (currentAttemptId == null) {
            return Optional.empty();
        }
        return attempts.stream()
                .filter(a -> a.attemptId().equals(currentAttemptId))
                .findFirst();
    }

    public boolean isCurrentAttempt(UUID attemptId) {
        return currentAttemptId != null && currentAttemptId.equals(attemptId) && status.isActive();
    }

    public String nodeId() {
        return nodeId;
    }

    public String nodeType() {
        return nodeType;
    }

    public NodeStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public UUID currentAttemptId() {
        return currentAttemptId;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant resumeAt() {
        return resumeAt;
    }

    public JsonNode input() {
        return input;
    }

    public JsonNode output() {
        return output;
    }

    public String branch() {
        return branch;
    }

    public NodeFailure error() {
        return error;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public List<NodeAttempt> attempts() {
        return List.copyOf(attempts);
    }
}
