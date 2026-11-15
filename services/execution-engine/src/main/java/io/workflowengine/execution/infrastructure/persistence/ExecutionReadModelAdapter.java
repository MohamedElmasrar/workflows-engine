package io.workflowengine.execution.infrastructure.persistence;

import io.workflowengine.execution.application.port.out.ExecutionReadModel;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionFilter;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionSummary;
import io.workflowengine.execution.application.query.ExecutionQueries.HistoryEntryView;
import io.workflowengine.execution.application.query.ExecutionQueries.Page;
import io.workflowengine.execution.application.query.ExecutionQueries.Stats;
import io.workflowengine.execution.domain.model.Actor;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read side for lists, history and the dashboard. Filtering uses composable Specifications; visibility is an EXISTS
 * subquery on the ACL projection. {@link #stats} stays SQL: one scan with five {@code count(*) FILTER (...)}
 * aggregates instead of five count queries.
 */
@Repository
@RequiredArgsConstructor
public class ExecutionReadModelAdapter implements ExecutionReadModel {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final ExecutionSummaryJpaRepository summaries;
    private final ExecutionEventJpaRepository events;
    private final WorkflowExecutionJpaRepository executions;
    private final JdbcTemplate jdbc;

    @Override
    public Page<ExecutionSummary> search(ExecutionFilter filter, Actor actor) {
        Specification<ExecutionSummaryEntity> spec = visibleTo(actor)
                .and(equal("workflowId", filter.workflowId()))
                .and(equal("status", filter.status()))
                .and(createdFrom(filter.from()))
                .and(createdBefore(filter.to()));
        var page = summaries.findAll(spec, PageRequest.of(filter.page(), filter.size(), NEWEST_FIRST));
        return new Page<>(
                page.getContent().stream()
                        .map(ExecutionReadModelAdapter::toSummary)
                        .toList(),
                page.getTotalElements(),
                filter.page(),
                filter.size());
    }

    @Override
    public List<ExecutionSummary> recent(Actor actor, int limit) {
        return summaries.findAll(visibleTo(actor), PageRequest.of(0, limit, NEWEST_FIRST)).getContent().stream()
                .map(ExecutionReadModelAdapter::toSummary)
                .toList();
    }

    @Override
    public List<HistoryEntryView> history(UUID executionId, long afterSequence, int limit) {
        return events
                .findByExecutionIdAndSequenceGreaterThanOrderBySequence(executionId, afterSequence, Limit.of(limit))
                .stream()
                .map(e -> new HistoryEntryView(
                        e.getSequence(),
                        e.getEventType(),
                        e.getNodeId(),
                        e.getAttemptId(),
                        e.getStatus(),
                        e.getDetails(),
                        e.getOccurredAt()))
                .toList();
    }

    @Override
    public Optional<String> status(UUID executionId) {
        return executions.findStatusById(executionId);
    }

    @Override
    public Stats stats(Actor actor, Instant since) {
        Timestamp ts = Timestamp.from(since);
        String sql =
                """
                select count(*) filter (where e.created_at >= ?),
                       count(*) filter (where e.created_at >= ? and e.status = 'COMPLETED'),
                       count(*) filter (where e.created_at >= ? and e.status in ('FAILED', 'TIMED_OUT')),
                       count(*) filter (where e.status = 'RUNNING'),
                       count(*) filter (where e.status = 'PENDING')
                from workflow_execution e
                """;
        if (actor.admin()) {
            return jdbc.queryForObject(sql, ExecutionReadModelAdapter::stats, ts, ts, ts);
        }
        return jdbc.queryForObject(
                sql
                        + " where exists (select 1 from workflow_access a where a.workflow_id = e.workflow_id and a.user_id = ?)",
                ExecutionReadModelAdapter::stats,
                ts,
                ts,
                ts,
                actor.userId());
    }

    // ---------------------------------------------------------------- specifications

    static Specification<ExecutionSummaryEntity> visibleTo(Actor actor) {
        if (actor.admin()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            Subquery<Integer> acl = query.subquery(Integer.class);
            Root<WorkflowAccessEntity> a = acl.from(WorkflowAccessEntity.class);
            acl.select(cb.literal(1))
                    .where(
                            cb.equal(a.get("workflowId"), root.get("workflowId")),
                            cb.equal(a.get("userId"), actor.userId()));
            return cb.exists(acl);
        };
    }

    private static Specification<ExecutionSummaryEntity> equal(String attribute, Object value) {
        return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(attribute), value);
    }

    private static Specification<ExecutionSummaryEntity> createdFrom(Instant from) {
        return (root, query, cb) ->
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from);
    }

    private static Specification<ExecutionSummaryEntity> createdBefore(Instant to) {
        return (root, query, cb) -> to == null ? cb.conjunction() : cb.lessThan(root.<Instant>get("createdAt"), to);
    }

    private static ExecutionSummary toSummary(ExecutionSummaryEntity e) {
        return new ExecutionSummary(
                e.getId(),
                e.getWorkflowId(),
                e.getWorkflowName(),
                e.getWorkflowVersion(),
                e.getStatus(),
                e.getTriggerType(),
                e.getTriggeredBy(),
                e.getCreatedAt(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getErrorCode(),
                e.getErrorMessage(),
                e.getRetryOf());
    }

    private static Stats stats(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Stats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5));
    }
}
