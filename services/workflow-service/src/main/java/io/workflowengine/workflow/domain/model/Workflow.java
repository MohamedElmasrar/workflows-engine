package io.workflowengine.workflow.domain.model;

import io.workflowengine.workflow.domain.event.WorkflowDomainEvent;
import io.workflowengine.workflow.domain.exception.InvalidWorkflowException;
import io.workflowengine.workflow.domain.exception.RevisionConflictException;
import io.workflowengine.workflow.domain.exception.WorkflowStateException;
import io.workflowengine.workflow.domain.policy.WorkflowAccessPolicy;
import io.workflowengine.workflow.domain.service.ValidationResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Workflow aggregate: metadata, the editable draft definition and the access-control list. Published versions are
 * separate immutable objects created by {@link #publish}.
 */
@Getter
@Accessors(fluent = true)
public final class Workflow {

    private final UUID id;
    private final String ownerId;
    private final Instant createdAt;
    private String name;
    private String description;
    private WorkflowStatus status;
    private WorkflowDefinition draft;
    private long revision;
    private Integer latestVersion;
    private Long publishedRevision;
    private long aclRevision;
    private Instant updatedAt;
    private Instant archivedAt;

    @Getter(AccessLevel.NONE)
    private final Map<String, Permission> permissions;

    @Getter(AccessLevel.NONE)
    private final List<WorkflowDomainEvent> pendingEvents = new ArrayList<>();

    private Workflow(
            UUID id,
            String ownerId,
            String name,
            String description,
            WorkflowStatus status,
            WorkflowDefinition draft,
            long revision,
            Integer latestVersion,
            Long publishedRevision,
            long aclRevision,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            Collection<Permission> permissions) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = requireName(name);
        this.description = description;
        this.status = Objects.requireNonNull(status);
        this.draft = Objects.requireNonNull(draft);
        this.revision = revision;
        this.latestVersion = latestVersion;
        this.publishedRevision = publishedRevision;
        this.aclRevision = aclRevision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
        this.permissions = new LinkedHashMap<>();
        permissions.forEach(p -> this.permissions.put(p.userId(), p));
    }

    public static Workflow create(
            UUID id, String ownerId, String name, String description, WorkflowDefinition draft, Instant now) {
        Workflow workflow = new Workflow(
                id,
                ownerId,
                name,
                description,
                WorkflowStatus.DRAFT,
                draft == null ? WorkflowDefinition.initial() : draft,
                1,
                null,
                null,
                1,
                now,
                now,
                null,
                List.of());
        workflow.recordAccessChange();
        return workflow;
    }

    /** Rebuilds an aggregate from persisted state. Does not record events. */
    public static Workflow restore(
            UUID id,
            String ownerId,
            String name,
            String description,
            WorkflowStatus status,
            WorkflowDefinition draft,
            long revision,
            Integer latestVersion,
            Long publishedRevision,
            long aclRevision,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            Collection<Permission> permissions) {
        return new Workflow(
                id,
                ownerId,
                name,
                description,
                status,
                draft,
                revision,
                latestVersion,
                publishedRevision,
                aclRevision,
                createdAt,
                updatedAt,
                archivedAt,
                permissions);
    }

    public void updateDraft(
            Actor actor,
            long expectedRevision,
            String newName,
            String newDescription,
            WorkflowDefinition newDraft,
            Instant now) {
        WorkflowAccessPolicy.require(this, actor, WorkflowAction.EDIT);
        requireNotArchived();
        if (expectedRevision != revision) {
            throw new RevisionConflictException(expectedRevision, revision);
        }
        this.name = requireName(newName);
        this.description = newDescription;
        this.draft = Objects.requireNonNull(newDraft);
        this.revision++;
        this.updatedAt = now;
    }

    /**
     * Publishes the current draft as the next immutable version. The caller passes the validation result for the
     * current draft so the aggregate can refuse invalid graphs without depending on the validator implementation.
     */
    public WorkflowVersion publish(Actor actor, ValidationResult validation, Instant now) {
        WorkflowAccessPolicy.require(this, actor, WorkflowAction.PUBLISH);
        requireNotArchived();
        if (!validation.valid()) {
            throw new InvalidWorkflowException(validation);
        }
        int next = latestVersion == null ? 1 : latestVersion + 1;
        WorkflowVersion version = new WorkflowVersion(id, next, name, draft, actor.userId(), now);
        this.latestVersion = next;
        this.publishedRevision = revision;
        this.status = WorkflowStatus.ACTIVE;
        this.updatedAt = now;
        pendingEvents.add(new WorkflowDomainEvent.VersionPublished(id, ownerId, version));
        return version;
    }

    public void archive(Actor actor, Instant now) {
        WorkflowAccessPolicy.require(this, actor, WorkflowAction.ARCHIVE);
        if (status == WorkflowStatus.ARCHIVED) {
            return;
        }
        this.status = WorkflowStatus.ARCHIVED;
        this.archivedAt = now;
        this.updatedAt = now;
        pendingEvents.add(new WorkflowDomainEvent.Archived(id, now));
        recordAccessChange();
    }

    public void grant(Actor actor, String userId, WorkflowRole role, Instant now) {
        WorkflowAccessPolicy.require(this, actor, WorkflowAction.MANAGE_PERMISSIONS);
        requireNotArchived();
        if (role == WorkflowRole.OWNER) {
            throw new WorkflowStateException("Ownership cannot be granted; share as EDITOR or VIEWER");
        }
        if (userId.equals(ownerId)) {
            throw new WorkflowStateException("The owner already has full access");
        }
        Permission existing = permissions.get(userId);
        if (existing != null && existing.role() == role) {
            return;
        }
        permissions.put(userId, new Permission(userId, role, actor.userId(), now));
        recordAccessChange();
    }

    public void revoke(Actor actor, String userId) {
        WorkflowAccessPolicy.require(this, actor, WorkflowAction.MANAGE_PERMISSIONS);
        if (permissions.remove(userId) != null) {
            recordAccessChange();
        }
    }

    public Workflow duplicate(Actor actor, UUID newId, Instant now) {
        WorkflowAccessPolicy.require(this, actor, WorkflowAction.VIEW);
        String copyName = name.length() > 93 ? name.substring(0, 93) : name;
        return create(newId, actor.userId(), copyName + " (copy)", description, draft, now);
    }

    public Optional<WorkflowRole> roleOf(String userId) {
        return Optional.ofNullable(permissions.get(userId)).map(Permission::role);
    }

    public boolean hasUnpublishedChanges() {
        return publishedRevision == null || publishedRevision != revision;
    }

    public List<WorkflowDomainEvent> pullEvents() {
        List<WorkflowDomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    private void recordAccessChange() {
        aclRevision++;
        pendingEvents.add(new WorkflowDomainEvent.AccessChanged(
                id, ownerId, aclRevision, List.copyOf(permissions.values()), status == WorkflowStatus.ARCHIVED));
    }

    private void requireNotArchived() {
        if (status == WorkflowStatus.ARCHIVED) {
            throw new WorkflowStateException("Archived workflows are read-only");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("Workflow name must be 1-100 characters");
        }
        return name.strip();
    }

    public Collection<Permission> permissions() {
        return List.copyOf(permissions.values());
    }
}
