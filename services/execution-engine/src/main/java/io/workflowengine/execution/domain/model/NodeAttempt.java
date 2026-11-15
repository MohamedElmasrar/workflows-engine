package io.workflowengine.execution.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * One dispatch of a node to a worker. The attempt id fences results: only the node's current attempt may change its
 * state. The lease is the deadline by which a result must arrive.
 */
@Getter
@Accessors(fluent = true)
public final class NodeAttempt {

    public enum Status {
        DISPATCHED,
        RUNNING,
        COMPLETED,
        FAILED,
        LEASE_EXPIRED,
        CANCELLED
    }

    private final UUID attemptId;
    private final int number;
    private final Instant dispatchedAt;
    private Status status;
    private Instant leaseExpiresAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String workerId;
    private String errorCode;
    private String errorMessage;

    public NodeAttempt(UUID attemptId, int number, Instant dispatchedAt, Instant leaseExpiresAt) {
        this(attemptId, number, Status.DISPATCHED, dispatchedAt, leaseExpiresAt, null, null, null, null, null);
    }

    public NodeAttempt(
            UUID attemptId,
            int number,
            Status status,
            Instant dispatchedAt,
            Instant leaseExpiresAt,
            Instant startedAt,
            Instant finishedAt,
            String workerId,
            String errorCode,
            String errorMessage) {
        this.attemptId = attemptId;
        this.number = number;
        this.status = status;
        this.dispatchedAt = dispatchedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.workerId = workerId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    void started(String worker, Instant at, Instant newLease) {
        this.status = Status.RUNNING;
        this.workerId = worker;
        this.startedAt = at;
        this.leaseExpiresAt = newLease;
    }

    void finish(Status finalStatus, String worker, Instant at, NodeFailure failure) {
        this.status = finalStatus;
        if (worker != null) {
            this.workerId = worker;
        }
        this.finishedAt = at;
        if (failure != null) {
            this.errorCode = failure.code();
            this.errorMessage = failure.message();
        }
    }

    public boolean isOpen() {
        return status == Status.DISPATCHED || status == Status.RUNNING;
    }
}
