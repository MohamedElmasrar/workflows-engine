package io.workflowengine.workflow.domain.event;

import io.workflowengine.workflow.domain.model.Permission;
import io.workflowengine.workflow.domain.model.WorkflowVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Facts recorded by the {@code Workflow} aggregate, translated to integration events by the application layer. */
public sealed interface WorkflowDomainEvent {

    UUID workflowId();

    record AccessChanged(
            UUID workflowId, String ownerId, long aclRevision, List<Permission> permissions, boolean archived)
            implements WorkflowDomainEvent {}

    record VersionPublished(UUID workflowId, String ownerId, WorkflowVersion version) implements WorkflowDomainEvent {}

    record Archived(UUID workflowId, Instant archivedAt) implements WorkflowDomainEvent {}
}
