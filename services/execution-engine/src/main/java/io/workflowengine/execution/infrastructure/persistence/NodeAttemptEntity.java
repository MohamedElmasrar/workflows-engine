package io.workflowengine.execution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One dispatch of a node to a worker, including its task lease. */
@Entity
@Table(name = "node_attempt")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeAttemptEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "attempt_id")
    private UUID attemptId;

    @Setter(AccessLevel.NONE)
    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;

    @Setter(AccessLevel.NONE)
    @Column(name = "node_id", nullable = false, updatable = false)
    private String nodeId;

    @Setter(AccessLevel.NONE)
    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private String status;

    @Column(name = "worker_id")
    private String workerId;

    @Setter(AccessLevel.NONE)
    @Column(name = "dispatched_at", nullable = false, updatable = false)
    private Instant dispatchedAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    NodeAttemptEntity(UUID attemptId, UUID executionId, String nodeId, int attemptNumber, Instant dispatchedAt) {
        this.attemptId = attemptId;
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.attemptNumber = attemptNumber;
        this.dispatchedAt = dispatchedAt;
    }
}
