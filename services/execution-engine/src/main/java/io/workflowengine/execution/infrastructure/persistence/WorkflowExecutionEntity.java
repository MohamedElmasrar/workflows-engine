package io.workflowengine.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Root row of the execution aggregate. Its {@code version} is the aggregate's optimistic lock. */
@Entity
@Table(name = "workflow_execution")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowExecutionEntity {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "workflow_version", nullable = false, updatable = false)
    private int workflowVersion;

    @Column(name = "workflow_name", nullable = false, updatable = false)
    private String workflowName;

    @Column(nullable = false)
    private String status;

    @Column(name = "trigger_type", nullable = false, updatable = false)
    private String triggerType;

    @Column(name = "triggered_by", nullable = false, updatable = false)
    private String triggeredBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private JsonNode input;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode output;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    @Column(name = "retry_of", updatable = false)
    private UUID retryOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    @Version
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long version;

    WorkflowExecutionEntity(UUID id) {
        this.id = id;
    }
}
