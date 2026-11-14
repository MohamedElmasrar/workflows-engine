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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "workflow_version")
@IdClass(WorkflowVersionEntity.Key.class)
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

    protected WorkflowVersionEntity() {}

    public WorkflowVersionEntity(
            UUID workflowId, int version, String name, JsonNode definition, int schemaVersion, String publishedBy,
            Instant publishedAt) {
        this.workflowId = workflowId;
        this.version = version;
        this.name = name;
        this.definition = definition;
        this.schemaVersion = schemaVersion;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public UUID getWorkflowId() { return workflowId; }
    public int getVersion() { return version; }
    public String getName() { return name; }
    public JsonNode getDefinition() { return definition; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
}
