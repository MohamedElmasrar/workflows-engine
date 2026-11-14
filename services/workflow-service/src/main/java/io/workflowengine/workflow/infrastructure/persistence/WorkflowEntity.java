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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow")
public class WorkflowEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
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
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** JPA optimistic lock protecting against lost updates between concurrent transactions. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size = 100)
    @CollectionTable(name = "workflow_permission", joinColumns = @JoinColumn(name = "workflow_id"))
    private Set<PermissionEmbeddable> permissions = new HashSet<>();

    protected WorkflowEntity() {}

    public WorkflowEntity(UUID id, String ownerId, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public JsonNode getDraftDefinition() {
        return draftDefinition;
    }

    public void setDraftDefinition(JsonNode draftDefinition) {
        this.draftDefinition = draftDefinition;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public Integer getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(Integer latestVersion) {
        this.latestVersion = latestVersion;
    }

    public Long getPublishedRevision() {
        return publishedRevision;
    }

    public void setPublishedRevision(Long publishedRevision) {
        this.publishedRevision = publishedRevision;
    }

    public long getAclRevision() {
        return aclRevision;
    }

    public void setAclRevision(long aclRevision) {
        this.aclRevision = aclRevision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Set<PermissionEmbeddable> getPermissions() {
        return permissions;
    }
}
