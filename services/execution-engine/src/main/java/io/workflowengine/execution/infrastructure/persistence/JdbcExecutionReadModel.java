package io.workflowengine.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.execution.application.port.out.ExecutionReadModel;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionFilter;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionSummary;
import io.workflowengine.execution.application.query.ExecutionQueries.HistoryEntryView;
import io.workflowengine.execution.application.query.ExecutionQueries.Page;
import io.workflowengine.execution.application.query.ExecutionQueries.Stats;
import io.workflowengine.execution.domain.model.Actor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL read model for lists, history and dashboard statistics. Visibility is a join on the ACL projection. */
@Repository
public class JdbcExecutionReadModel implements ExecutionReadModel {

    private static final String SUMMARY_COLUMNS = """
            e.id, e.workflow_id, e.workflow_name, e.workflow_version, e.status, e.trigger_type, e.triggered_by,
            e.created_at, e.started_at, e.finished_at, e.error_code, e.error_message, e.retry_of
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcExecutionReadModel(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<ExecutionSummary> search(ExecutionFilter filter, Actor actor) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        visibility(actor, where, args);
        if (filter.workflowId() != null) {
            where.append(" and e.workflow_id = ?");
            args.add(filter.workflowId());
        }
        if (filter.status() != null) {
            where.append(" and e.status = ?");
            args.add(filter.status());
        }
        if (filter.from() != null) {
            where.append(" and e.created_at >= ?");
            args.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" and e.created_at < ?");
            args.add(Timestamp.from(filter.to()));
        }
        Long total = jdbc.queryForObject("select count(*) from workflow_execution e" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(filter.size());
        pageArgs.add((long) filter.page() * filter.size());
        List<ExecutionSummary> items = jdbc.query(
                "select " + SUMMARY_COLUMNS + " from workflow_execution e" + where
                        + " order by e.created_at desc, e.id limit ? offset ?",
                JdbcExecutionReadModel::summary,
                pageArgs.toArray());
        return new Page<>(items, total == null ? 0 : total, filter.page(), filter.size());
    }

    @Override
    public List<HistoryEntryView> history(UUID executionId, long afterSequence, int limit) {
        return jdbc.query(
                """
                select sequence, event_type, node_id, attempt_id, status, details::text, occurred_at
                from execution_event where execution_id = ? and sequence > ? order by sequence limit ?
                """,
                (rs, i) -> new HistoryEntryView(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getObject(4, UUID.class),
                        rs.getString(5),
                        parse(rs.getString(6)),
                        rs.getTimestamp(7).toInstant()),
                executionId,
                afterSequence,
                limit);
    }

    @Override
    public Stats stats(Actor actor, Instant since) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        visibility(actor, where, args);
        List<Object> all = new ArrayList<>();
        all.add(Timestamp.from(since));
        all.add(Timestamp.from(since));
        all.add(Timestamp.from(since));
        all.addAll(args);
        return jdbc.queryForObject(
                """
                select count(*) filter (where e.created_at >= ?),
                       count(*) filter (where e.created_at >= ? and e.status = 'COMPLETED'),
                       count(*) filter (where e.created_at >= ? and e.status in ('FAILED', 'TIMED_OUT')),
                       count(*) filter (where e.status = 'RUNNING'),
                       count(*) filter (where e.status = 'PENDING')
                from workflow_execution e
                """ + where,
                (rs, i) -> new Stats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)),
                all.toArray());
    }

    @Override
    public List<ExecutionSummary> recent(Actor actor, int limit) {
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        visibility(actor, where, args);
        args.add(limit);
        return jdbc.query(
                "select " + SUMMARY_COLUMNS + " from workflow_execution e" + where + " order by e.created_at desc limit ?",
                JdbcExecutionReadModel::summary,
                args.toArray());
    }

    @Override
    public Optional<String> status(UUID executionId) {
        return jdbc.query("select status from workflow_execution where id = ?", (rs, i) -> rs.getString(1), executionId)
                .stream()
                .findFirst();
    }

    private static void visibility(Actor actor, StringBuilder where, List<Object> args) {
        if (!actor.admin()) {
            where.append(" and exists (select 1 from workflow_access a where a.workflow_id = e.workflow_id and a.user_id = ?)");
            args.add(actor.userId());
        }
    }

    private static ExecutionSummary summary(ResultSet rs, int i) throws SQLException {
        return new ExecutionSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("workflow_id", UUID.class),
                rs.getString("workflow_name"),
                rs.getInt("workflow_version"),
                rs.getString("status"),
                rs.getString("trigger_type"),
                rs.getString("triggered_by"),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getObject("retry_of", UUID.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private com.fasterxml.jackson.databind.JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
