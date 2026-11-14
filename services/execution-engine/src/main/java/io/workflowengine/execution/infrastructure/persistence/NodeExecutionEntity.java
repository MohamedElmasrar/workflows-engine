package io.workflowengine.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "node_execution")
@IdClass(NodeExecutionEntity.Key.class)
public class NodeExecutionEntity {

    public record Key(UUID executionId, String nodeId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "execution_id")
    UUID executionId;

    @Id
    @Column(name = "node_id")
    String nodeId;

    @Column(name = "node_type", nullable = false)
    String nodeType;

    @Column(nullable = false)
    String status;

    @Column(name = "attempt_count", nullable = false)
    int attemptCount;

    @Column(name = "current_attempt_id")
    UUID currentAttemptId;

    @Column(name = "available_at")
    Instant availableAt;

    @Column(name = "lease_expires_at")
    Instant leaseExpiresAt;

    @Column(name = "resume_at")
    Instant resumeAt;

    @JdbcTypeCode(SqlTypes.JSON)
    JsonNode input;

    @JdbcTypeCode(SqlTypes.JSON)
    JsonNode output;

    String branch;

    @Column(name = "error_code")
    String errorCode;

    @Column(name = "error_message")
    String errorMessage;

    @Column(name = "error_retryable")
    Boolean errorRetryable;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    protected NodeExecutionEntity() {}

    NodeExecutionEntity(UUID executionId, String nodeId, String nodeType) {
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }
}
