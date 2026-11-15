package io.workflowengine.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Persisted state transition (read side). Rows are appended with a JDBC batch insert by the repository. */
@Entity
@Immutable
@Table(name = "execution_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionEventEntity {

    @Id
    private long id;

    @Column(name = "execution_id")
    private UUID executionId;

    private long sequence;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "node_id")
    private String nodeId;

    @Column(name = "attempt_id")
    private UUID attemptId;

    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode details;

    @Column(name = "occurred_at")
    private Instant occurredAt;
}
