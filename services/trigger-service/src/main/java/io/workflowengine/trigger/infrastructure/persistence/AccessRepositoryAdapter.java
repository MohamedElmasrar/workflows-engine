package io.workflowengine.trigger.infrastructure.persistence;

import io.workflowengine.trigger.application.port.out.AccessRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccessRepositoryAdapter implements AccessRepository {

    private final WorkflowAccessJpaRepository access;
    private final JdbcTemplate jdbc;

    /** SQL on purpose: applies an ACL snapshot only if its revision is newer, atomically (out-of-order events). */
    @Override
    public boolean replace(UUID workflowId, String ownerId, long aclRevision, Map<String, String> roles) {
        int updated = jdbc.update(
                """
                insert into workflow_acl_revision (workflow_id, owner_id, acl_revision) values (?, ?, ?)
                on conflict (workflow_id) do update set owner_id = excluded.owner_id, acl_revision = excluded.acl_revision
                where workflow_acl_revision.acl_revision < excluded.acl_revision
                """,
                workflowId,
                ownerId,
                aclRevision);
        if (updated == 0) {
            return false;
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
    public Optional<String> roleOf(UUID workflowId, String userId) {
        return access.findById(new WorkflowAccessEntity.Key(workflowId, userId)).map(WorkflowAccessEntity::getRole);
    }
}
