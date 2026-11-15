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
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of {@code workflow_execution} with only the list columns, so paginated history queries never load
 * the (up to 256 KiB) input/output JSON documents.
 */
@Entity
@Immutable
@Table(name = "workflow_execution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionSummaryEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(name = "workflow_version")
    private int workflowVersion;

    private String status;

    @Column(name = "trigger_type")
    private String triggerType;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_of")
    private UUID retryOf;
}
