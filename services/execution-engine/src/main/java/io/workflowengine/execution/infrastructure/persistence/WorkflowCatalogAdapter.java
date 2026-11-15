package io.workflowengine.execution.infrastructure.persistence;

import io.workflowengine.execution.application.port.out.WorkflowCatalog;
import io.workflowengine.execution.domain.model.PublishedVersion;
import io.workflowengine.execution.domain.model.WorkflowAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Projections of workflow-service data. Reads, the scheduling lock and archiving use Spring Data. The two writes that
 * apply incoming events stay SQL because they must be atomic under concurrent consumers:
 * {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE acl_revision < excluded.acl_revision} drops out-of-order ACL
 * snapshots, and {@code ON CONFLICT DO NOTHING} makes version snapshots idempotent. The JPA equivalent (select, then
 * insert or update) has a race window between the two steps.
 */
@Repository
@RequiredArgsConstructor
public class WorkflowCatalogAdapter implements WorkflowCatalog {

    private final WorkflowProjectionJpaRepository projections;
    private final WorkflowAccessJpaRepository accessEntries;
    private final WorkflowVersionSnapshotJpaRepository snapshots;
    private final JdbcTemplate jdbc;

    @Override
    public Optional<WorkflowAccess> access(UUID workflowId) {
        return projections
                .findById(workflowId)
                .map(p -> new WorkflowAccess(
                        workflowId,
                        p.getOwnerId(),
                        p.isArchived(),
                        accessEntries.findByWorkflowId(workflowId).stream()
                                .collect(Collectors.toMap(
                                        WorkflowAccessEntity::getUserId, WorkflowAccessEntity::getRole))));
    }

    @Override
    public Optional<PublishedVersion> version(UUID workflowId, int version) {
        return snapshots
                .findById(new WorkflowVersionSnapshotEntity.Key(workflowId, version))
                .map(WorkflowCatalogAdapter::toDomain);
    }

    @Override
    public Optional<PublishedVersion> latestVersion(UUID workflowId) {
        return snapshots.findFirstByWorkflowIdOrderByVersionDesc(workflowId).map(WorkflowCatalogAdapter::toDomain);
    }

    @Override
    public void lockForScheduling(UUID workflowId) {
        projections.findLockedByWorkflowId(workflowId);
    }

    @Override
    public boolean replaceAccess(
            UUID workflowId, String ownerId, long aclRevision, boolean archived, Map<String, String> roles) {
        int updated = jdbc.update(
                """
                insert into workflow_projection (workflow_id, owner_id, archived, acl_revision, updated_at)
                values (?, ?, ?, ?, now())
                on conflict (workflow_id) do update
                    set owner_id = excluded.owner_id, archived = excluded.archived or workflow_projection.archived,
                        acl_revision = excluded.acl_revision, updated_at = now()
                    where workflow_projection.acl_revision < excluded.acl_revision
                """,
                workflowId,
                ownerId,
                archived,
                aclRevision);
        if (updated == 0) {
            return false; // older or equal revision: ignore out-of-order event
        }
        jdbc.update("delete from workflow_access where workflow_id = ?", workflowId);
        jdbc.batchUpdate(
                "insert into workflow_access (workflow_id, user_id, role) values (?, ?, ?)",
                roles.entrySet().stream()
                        .map(e -> new Object[] {workflowId, e.getKey(), e.getValue()})
                        .toList());
        return true;
    }

    @Override
    public void saveVersion(PublishedVersion version, String ownerId, Instant publishedAt) {
        jdbc.update(
                """
                insert into workflow_version_snapshot (workflow_id, version, name, definition, published_at)
                values (?, ?, ?, ?::jsonb, ?) on conflict do nothing
                """,
                version.workflowId(),
                version.version(),
                version.name(),
                version.definition().toString(),
                Timestamp.from(publishedAt));
        jdbc.update(
                """
                insert into workflow_projection (workflow_id, owner_id, name, latest_version, updated_at)
                values (?, ?, ?, ?, now())
                on conflict (workflow_id) do update
                    set name = case when coalesce(workflow_projection.latest_version, 0) <= excluded.latest_version
                                    then excluded.name else workflow_projection.name end,
                        latest_version = greatest(coalesce(workflow_projection.latest_version, 0), excluded.latest_version),
                        updated_at = now()
                """,
                version.workflowId(),
                ownerId,
                version.name(),
                version.version());
    }

    @Override
    public void markArchived(UUID workflowId) {
        projections.markArchived(workflowId, Instant.now());
    }

    private static PublishedVersion toDomain(WorkflowVersionSnapshotEntity s) {
        return new PublishedVersion(s.getWorkflowId(), s.getVersion(), s.getName(), s.getDefinition());
    }
}
