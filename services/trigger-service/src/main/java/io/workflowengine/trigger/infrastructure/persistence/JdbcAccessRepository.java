package io.workflowengine.trigger.infrastructure.persistence;

import io.workflowengine.trigger.application.port.out.AccessRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccessRepository implements AccessRepository {

    private final JdbcTemplate jdbc;

    public JdbcAccessRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
        return jdbc
                .query(
                        "select role from workflow_access where workflow_id = ? and user_id = ?",
                        (rs, i) -> rs.getString(1),
                        workflowId,
                        userId)
                .stream()
                .findFirst();
    }
}
