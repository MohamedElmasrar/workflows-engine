package io.workflowengine.workflow.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowEntity {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String ownerId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_definition", nullable = false)
    private JsonNode draftDefinition;

    /** Domain-level draft revision used for client-side optimistic concurrency. */
    @Column(nullable = false)
    private long revision;

    @Column(name = "latest_version")
    private Integer latestVersion;

    @Column(name = "published_revision")
    private Long publishedRevision;

    @Column(name = "acl_revision", nullable = false)
    private long aclRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** JPA optimistic lock protecting against lost updates between concurrent transactions. */
    @Version
    @Column(name = "lock_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private long lockVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "workflow_permission", joinColumns = @JoinColumn(name = "workflow_id"))
    private Set<PermissionEmbeddable> permissions = new HashSet<>();

    public WorkflowEntity(UUID id, String ownerId, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }
}
