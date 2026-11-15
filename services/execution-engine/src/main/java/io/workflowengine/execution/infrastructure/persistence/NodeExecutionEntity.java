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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "node_execution")
@IdClass(NodeExecutionEntity.Key.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeExecutionEntity {

    public record Key(UUID executionId, String nodeId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "execution_id")
    private UUID executionId;

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "node_id")
    private String nodeId;

    @Setter(AccessLevel.NONE)
    @Column(name = "node_type", nullable = false)
    private String nodeType;

    @Column(nullable = false)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "current_attempt_id")
    private UUID currentAttemptId;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "resume_at")
    private Instant resumeAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode input;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode output;

    private String branch;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "error_retryable")
    private Boolean errorRetryable;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    NodeExecutionEntity(UUID executionId, String nodeId, String nodeType) {
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }
}
