package io.workflowengine.execution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_attempt")
public class NodeAttemptEntity {

    @Id
    @Column(name = "attempt_id")
    UUID attemptId;

    @Column(name = "execution_id", nullable = false, updatable = false)
    UUID executionId;

    @Column(name = "node_id", nullable = false, updatable = false)
    String nodeId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    int attemptNumber;

    @Column(nullable = false)
    String status;

    @Column(name = "worker_id")
    String workerId;

    @Column(name = "dispatched_at", nullable = false, updatable = false)
    Instant dispatchedAt;

    @Column(name = "lease_expires_at")
    Instant leaseExpiresAt;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Column(name = "error_code")
    String errorCode;

    @Column(name = "error_message")
    String errorMessage;

    protected NodeAttemptEntity() {}

    NodeAttemptEntity(UUID attemptId, UUID executionId, String nodeId, int attemptNumber, Instant dispatchedAt) {
        this.attemptId = attemptId;
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.attemptNumber = attemptNumber;
        this.dispatchedAt = dispatchedAt;
    }
}
