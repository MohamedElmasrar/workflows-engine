package io.workflowengine.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.execution.application.port.out.WorkflowCatalog;
import io.workflowengine.execution.domain.model.PublishedVersion;
import io.workflowengine.execution.domain.model.WorkflowAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowCatalog implements WorkflowCatalog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowCatalog(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkflowAccess> access(UUID workflowId) {
        List<Object[]> projection = jdbc.query(
                "select owner_id, archived from workflow_projection where workflow_id = ?",
                (rs, i) -> new Object[] {rs.getString(1), rs.getBoolean(2)},
                workflowId);
        if (projection.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> roles = new HashMap<>();
        jdbc.query(
                "select user_id, role from workflow_access where workflow_id = ?",
                rs -> {
                    roles.put(rs.getString(1), rs.getString(2));
                },
                workflowId);
        return Optional.of(new WorkflowAccess(
                workflowId, (String) projection.get(0)[0], (Boolean) projection.get(0)[1], roles));
    }

    @Override
    public Optional<PublishedVersion> version(UUID workflowId, int version) {
        return jdbc
                .query(
                        "select name, definition::text from workflow_version_snapshot where workflow_id = ? and version = ?",
                        (rs, i) -> new PublishedVersion(workflowId, version, rs.getString(1), parse(rs.getString(2))),
                        workflowId,
                        version)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PublishedVersion> latestVersion(UUID workflowId) {
        return jdbc
                .query(
                        """
                        select version, name, definition::text from workflow_version_snapshot
                        where workflow_id = ? order by version desc limit 1
                        """,
                        (rs, i) ->
                                new PublishedVersion(workflowId, rs.getInt(1), rs.getString(2), parse(rs.getString(3))),
                        workflowId)
                .stream()
                .findFirst();
    }

    @Override
    public void lockForScheduling(UUID workflowId) {
        jdbc.query("select 1 from workflow_projection where workflow_id = ? for update", rs -> {}, workflowId);
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
        jdbc.update(
                "update workflow_projection set archived = true, updated_at = now() where workflow_id = ?", workflowId);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt workflow definition snapshot", e);
        }
    }
}
