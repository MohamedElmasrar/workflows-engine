package io.workflowengine.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_execution")
public class WorkflowExecutionEntity {

    @Id
    UUID id;

    @Column(name = "workflow_id", nullable = false, updatable = false)
    UUID workflowId;

    @Column(name = "workflow_version", nullable = false, updatable = false)
    int workflowVersion;

    @Column(name = "workflow_name", nullable = false, updatable = false)
    String workflowName;

    @Column(nullable = false)
    String status;

    @Column(name = "trigger_type", nullable = false, updatable = false)
    String triggerType;

    @Column(name = "triggered_by", nullable = false, updatable = false)
    String triggeredBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    JsonNode input;

    @JdbcTypeCode(SqlTypes.JSON)
    JsonNode output;

    @Column(name = "error_code")
    String errorCode;

    @Column(name = "error_message")
    String errorMessage;

    @Column(name = "idempotency_key", updatable = false)
    String idempotencyKey;

    @Column(name = "retry_of", updatable = false)
    UUID retryOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Column(name = "deadline_at")
    Instant deadlineAt;

    @Column(name = "event_sequence", nullable = false)
    long eventSequence;

    /** Optimistic lock of the aggregate root; force-incremented on every modification of the execution. */
    @Version
    @Column(nullable = false)
    long version;

    protected WorkflowExecutionEntity() {}

    WorkflowExecutionEntity(UUID id) {
        this.id = id;
    }
}
