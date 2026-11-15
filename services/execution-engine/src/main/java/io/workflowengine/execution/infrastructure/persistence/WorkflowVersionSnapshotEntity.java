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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable copy of a published workflow version. */
@Entity
@Immutable
@Table(name = "workflow_version_snapshot")
@IdClass(WorkflowVersionSnapshotEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowVersionSnapshotEntity {

    public record Key(UUID workflowId, int version) implements Serializable {
        public Key() {
            this(null, 0);
        }
    }

    @Id
    @Column(name = "workflow_id")
    private UUID workflowId;

    @Id
    private int version;

    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode definition;

    @Column(name = "published_at")
    private Instant publishedAt;
}
