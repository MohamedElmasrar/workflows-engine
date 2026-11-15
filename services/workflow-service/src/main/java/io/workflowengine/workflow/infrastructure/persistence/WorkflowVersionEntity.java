package io.workflowengine.workflow.infrastructure.persistence;

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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "workflow_version")
@IdClass(WorkflowVersionEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WorkflowVersionEntity {

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

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode definition;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "published_by", nullable = false)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
