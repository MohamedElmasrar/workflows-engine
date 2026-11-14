package io.workflowengine.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.execution.application.port.out.ExecutionRepository;
import io.workflowengine.execution.application.port.out.WorkflowCatalog;
import io.workflowengine.execution.domain.model.EngineSettings;
import io.workflowengine.execution.domain.model.ExecutionError;
import io.workflowengine.execution.domain.model.ExecutionGraph;
import io.workflowengine.execution.domain.model.ExecutionHistoryEntry;
import io.workflowengine.execution.domain.model.ExecutionStatus;
import io.workflowengine.execution.domain.model.NodeAttempt;
import io.workflowengine.execution.domain.model.NodeExecution;
import io.workflowengine.execution.domain.model.NodeFailure;
import io.workflowengine.execution.domain.model.NodeStatus;
import io.workflowengine.execution.domain.model.TriggerType;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Maps the {@link WorkflowExecution} aggregate to its tables. Entities are loaded explicitly (no JPA associations), so
 * the aggregate is always read with three queries regardless of its size, and only changed rows are updated.
 */
@Repository
public class JpaExecutionRepository implements ExecutionRepository {

    private static final int MAX_TEXT = 2000;

    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final WorkflowCatalog catalog;
    private final EngineSettings settings;
    private final ObjectMapper objectMapper;

    public JpaExecutionRepository(
            EntityManager em,
            JdbcTemplate jdbc,
            WorkflowCatalog catalog,
            EngineSettings settings,
            ObjectMapper objectMapper) {
        this.em = em;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkflowExecution> loadForUpdate(UUID id) {
        return Optional.ofNullable(em.find(WorkflowExecutionEntity.class, id, LockModeType.OPTIMISTIC_FORCE_INCREMENT))
                .map(this::toDomain);
    }

    @Override
    public Optional<WorkflowExecution> find(UUID id) {
        return Optional.ofNullable(em.find(WorkflowExecutionEntity.class, id)).map(this::toDomain);
    }

    @Override
    public boolean exists(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("select exists(select 1 from workflow_execution where id = ?)", Boolean.class, id));
    }

    @Override
    public Optional<UUID> findIdByIdempotencyKey(UUID workflowId, String key) {
        return jdbc
                .query(
                        "select id from workflow_execution where workflow_id = ? and idempotency_key = ?",
                        (rs, i) -> rs.getObject(1, UUID.class),
                        workflowId,
                        key)
                .stream()
                .findFirst();
    }

    @Override
    public void insert(WorkflowExecution execution) {
        WorkflowExecutionEntity root = new WorkflowExecutionEntity(execution.id());
        copyRoot(execution, root);
        em.persist(root);
        for (NodeExecution node : execution.nodes()) {
            NodeExecutionEntity entity = new NodeExecutionEntity(execution.id(), node.nodeId(), node.nodeType());
            copyNode(node, entity);
            em.persist(entity);
        }
        em.flush();
        syncAttempts(execution);
        em.flush();
        appendHistory(execution.id(), execution.pullHistory());
    }

    @Override
    public void update(WorkflowExecution execution) {
        WorkflowExecutionEntity root = em.find(WorkflowExecutionEntity.class, execution.id());
        copyRoot(execution, root);
        Map<String, NodeExecutionEntity> entities = nodeEntities(execution.id());
        for (NodeExecution node : execution.nodes()) {
            copyNode(node, entities.get(node.nodeId()));
        }
        syncAttempts(execution);
        em.flush();
        appendHistory(execution.id(), execution.pullHistory());
    }

    @Override
    public long countRunning(UUID workflowId) {
        Long count = jdbc.queryForObject(
                "select count(*) from workflow_execution where workflow_id = ? and status = 'RUNNING'",
                Long.class,
                workflowId);
        return count == null ? 0 : count;
    }

    @Override
    public List<UUID> findDue(Instant now, int limit) {
        Timestamp ts = Timestamp.from(now);
        return jdbc.query(
                """
                select execution_id from (
                    select execution_id from node_execution where status = 'READY' and available_at <= ?
                    union
                    select execution_id from node_execution where status in ('QUEUED', 'RUNNING') and lease_expires_at <= ?
                    union
                    select execution_id from node_execution where status = 'RUNNING' and resume_at <= ?
                    union
                    select id from workflow_execution where status = 'RUNNING' and deadline_at <= ?
                ) due limit ?
                """,
                (rs, i) -> rs.getObject(1, UUID.class),
                ts,
                ts,
                ts,
                ts,
                limit);
    }

    @Override
    public List<UUID> findPending(int limit) {
        return jdbc.query(
                "select id from workflow_execution where status = 'PENDING' order by created_at limit ?",
                (rs, i) -> rs.getObject(1, UUID.class),
                limit);
    }

    @Override
    public long countActive() {
        Long count = jdbc.queryForObject(
                "select count(*) from workflow_execution where status in ('PENDING', 'RUNNING')", Long.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------ mapping

    private WorkflowExecution toDomain(WorkflowExecutionEntity e) {
        ExecutionGraph graph = ExecutionGraph.parse(catalog.version(e.workflowId, e.workflowVersion)
                .orElseThrow(() ->
                        new IllegalStateException("Missing version snapshot " + e.workflowId + "@" + e.workflowVersion))
                .definition());
        Map<String, List<NodeAttemptEntity>> attempts = em.createQuery(
                        "select a from NodeAttemptEntity a where a.executionId = :id order by a.attemptNumber",
                        NodeAttemptEntity.class)
                .setParameter("id", e.id)
                .getResultStream()
                .collect(Collectors.groupingBy(a -> a.nodeId));
        Map<String, NodeExecutionEntity> nodeEntities = nodeEntities(e.id);
        List<NodeExecution> nodes = graph.nodes().stream()
                .map(n -> toDomain(nodeEntities.get(n.id()), attempts.getOrDefault(n.id(), List.of())))
                .toList();
        WorkflowExecution.State state = new WorkflowExecution.State(
                e.id,
                e.workflowId,
                e.workflowVersion,
                e.workflowName,
                ExecutionStatus.valueOf(e.status),
                TriggerType.valueOf(e.triggerType),
                e.triggeredBy,
                e.input,
                e.output,
                e.errorCode == null ? null : new ExecutionError(e.errorCode, e.errorMessage),
                e.idempotencyKey,
                e.retryOf,
                e.createdAt,
                e.startedAt,
                e.finishedAt,
                e.deadlineAt,
                e.eventSequence);
        return WorkflowExecution.restore(state, graph, nodes, settings);
    }

    private static NodeExecution toDomain(NodeExecutionEntity n, List<NodeAttemptEntity> attempts) {
        return new NodeExecution(
                n.nodeId,
                n.nodeType,
                NodeStatus.valueOf(n.status),
                n.attemptCount,
                n.currentAttemptId,
                n.availableAt,
                n.leaseExpiresAt,
                n.resumeAt,
                n.input,
                n.output,
                n.branch,
                n.errorCode == null
                        ? null
                        : new NodeFailure(n.errorCode, n.errorMessage, Boolean.TRUE.equals(n.errorRetryable)),
                n.startedAt,
                n.finishedAt,
                attempts.stream()
                        .map(a -> new NodeAttempt(
                                a.attemptId,
                                a.attemptNumber,
                                NodeAttempt.Status.valueOf(a.status),
                                a.dispatchedAt,
                                a.leaseExpiresAt,
                                a.startedAt,
                                a.finishedAt,
                                a.workerId,
                                a.errorCode,
                                a.errorMessage))
                        .toList());
    }

    private Map<String, NodeExecutionEntity> nodeEntities(UUID executionId) {
        return em.createQuery(
                        "select n from NodeExecutionEntity n where n.executionId = :id", NodeExecutionEntity.class)
                .setParameter("id", executionId)
                .getResultStream()
                .collect(Collectors.toMap(n -> n.nodeId, n -> n));
    }

    private static void copyRoot(WorkflowExecution execution, WorkflowExecutionEntity e) {
        WorkflowExecution.State s = execution.state();
        e.workflowId = s.workflowId();
        e.workflowVersion = s.workflowVersion();
        e.workflowName = s.workflowName();
        e.status = s.status().name();
        e.triggerType = s.triggerType().name();
        e.triggeredBy = s.triggeredBy();
        e.input = s.input();
        e.output = s.output();
        e.errorCode = s.error() == null ? null : s.error().code();
        e.errorMessage = s.error() == null ? null : truncate(s.error().message());
        e.idempotencyKey = s.idempotencyKey();
        e.retryOf = s.retryOf();
        e.createdAt = s.createdAt();
        e.startedAt = s.startedAt();
        e.finishedAt = s.finishedAt();
        e.deadlineAt = s.deadlineAt();
        e.eventSequence = s.eventSequence();
    }

    private static void copyNode(NodeExecution n, NodeExecutionEntity e) {
        e.status = n.status().name();
        e.attemptCount = n.attemptCount();
        e.currentAttemptId = n.currentAttemptId();
        e.availableAt = n.availableAt();
        e.leaseExpiresAt = n.leaseExpiresAt();
        e.resumeAt = n.resumeAt();
        e.input = n.input();
        e.output = n.output();
        e.branch = n.branch();
        e.errorCode = n.error() == null ? null : n.error().code();
        e.errorMessage = n.error() == null ? null : truncate(n.error().message());
        e.errorRetryable = n.error() == null ? null : n.error().retryable();
        e.startedAt = n.startedAt();
        e.finishedAt = n.finishedAt();
    }

    private void syncAttempts(WorkflowExecution execution) {
        for (NodeExecution node : execution.nodes()) {
            for (NodeAttempt attempt : node.attempts()) {
                NodeAttemptEntity entity = em.find(NodeAttemptEntity.class, attempt.attemptId());
                boolean isNew = entity == null;
                if (isNew) {
                    entity = new NodeAttemptEntity(
                            attempt.attemptId(),
                            execution.id(),
                            node.nodeId(),
                            attempt.number(),
                            attempt.dispatchedAt());
                }
                entity.status = attempt.status().name();
                entity.workerId = attempt.workerId();
                entity.leaseExpiresAt = attempt.leaseExpiresAt();
                entity.startedAt = attempt.startedAt();
                entity.finishedAt = attempt.finishedAt();
                entity.errorCode = attempt.errorCode();
                entity.errorMessage = truncate(attempt.errorMessage());
                if (isNew) {
                    em.persist(entity); // state is captured at persist time, so populate first
                }
            }
        }
    }

    private void appendHistory(UUID executionId, List<ExecutionHistoryEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                """
                insert into execution_event (execution_id, sequence, event_type, node_id, attempt_id, status, details, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                entries,
                entries.size(),
                (ps, h) -> {
                    ps.setObject(1, executionId);
                    ps.setLong(2, h.sequence());
                    ps.setString(3, h.type());
                    ps.setString(4, h.nodeId());
                    ps.setObject(5, h.attemptId());
                    ps.setString(6, h.status());
                    ps.setString(7, h.details() == null ? "{}" : writeJson(h.details()));
                    ps.setTimestamp(8, Timestamp.from(h.occurredAt()));
                });
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
