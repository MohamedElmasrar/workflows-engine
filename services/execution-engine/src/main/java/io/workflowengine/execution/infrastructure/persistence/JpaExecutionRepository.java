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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Maps the {@link WorkflowExecution} aggregate to its tables with Spring Data JPA. An aggregate is always read with
 * three queries (root, nodes, attempts) regardless of its size. Two things deliberately stay outside Spring Data:
 * <ul>
 *   <li>new rows are inserted with {@link EntityManager#persist}, because the ids are assigned by the domain and
 *       {@code save()} would issue a SELECT before every insert;</li>
 *   <li>{@link #findDue} and {@link #appendHistory} use SQL (a UNION over partial indexes and a JDBC batch insert).</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class JpaExecutionRepository implements ExecutionRepository {

    private static final int MAX_TEXT = 2000;

    private final WorkflowExecutionJpaRepository executions;
    private final NodeExecutionJpaRepository nodes;
    private final NodeAttemptJpaRepository attempts;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final WorkflowCatalog catalog;
    private final EngineSettings settings;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<WorkflowExecution> loadForUpdate(UUID id) {
        return executions.findForUpdateById(id).map(this::toDomain);
    }

    @Override
    public Optional<WorkflowExecution> find(UUID id) {
        return executions.findById(id).map(this::toDomain);
    }

    @Override
    public boolean exists(UUID id) {
        return executions.existsById(id);
    }

    @Override
    public Optional<UUID> findIdByIdempotencyKey(UUID workflowId, String key) {
        return executions.findIdByIdempotencyKey(workflowId, key);
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
        syncAttempts(execution, Map.of());
        em.flush(); // history rows reference the execution and are written with JDBC
        appendHistory(execution.id(), execution.pullHistory());
    }

    @Override
    public void update(WorkflowExecution execution) {
        // all three lookups hit the persistence context populated by loadForUpdate
        copyRoot(execution, executions.findById(execution.id()).orElseThrow());
        Map<String, NodeExecutionEntity> nodeEntities = nodes.findByExecutionId(execution.id()).stream()
                .collect(Collectors.toMap(NodeExecutionEntity::getNodeId, Function.identity()));
        for (NodeExecution node : execution.nodes()) {
            copyNode(node, nodeEntities.get(node.nodeId()));
        }
        syncAttempts(
                execution,
                attempts.findByExecutionIdOrderByAttemptNumber(execution.id()).stream()
                        .collect(Collectors.toMap(NodeAttemptEntity::getAttemptId, Function.identity())));
        em.flush();
        appendHistory(execution.id(), execution.pullHistory());
    }

    @Override
    public long countRunning(UUID workflowId) {
        return executions.countByWorkflowIdAndStatus(workflowId, ExecutionStatus.RUNNING.name());
    }

    /** Native SQL: one round trip over four partial indexes (see V1 migration). */
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
        return executions.findIdsByStatusOldestFirst(ExecutionStatus.PENDING.name(), PageRequest.of(0, limit));
    }

    @Override
    public long countActive() {
        return executions.countByStatusIn(List.of(ExecutionStatus.PENDING.name(), ExecutionStatus.RUNNING.name()));
    }

    // ------------------------------------------------------------------ mapping

    private WorkflowExecution toDomain(WorkflowExecutionEntity e) {
        ExecutionGraph graph = ExecutionGraph.parse(catalog.version(e.getWorkflowId(), e.getWorkflowVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing version snapshot " + e.getWorkflowId() + "@" + e.getWorkflowVersion()))
                .definition());
        Map<String, List<NodeAttemptEntity>> attemptsByNode =
                attempts.findByExecutionIdOrderByAttemptNumber(e.getId()).stream()
                        .collect(Collectors.groupingBy(NodeAttemptEntity::getNodeId));
        Map<String, NodeExecutionEntity> nodeEntities = nodes.findByExecutionId(e.getId()).stream()
                .collect(Collectors.toMap(NodeExecutionEntity::getNodeId, Function.identity()));
        List<NodeExecution> nodeList = graph.nodes().stream()
                .map(n -> toDomain(nodeEntities.get(n.id()), attemptsByNode.getOrDefault(n.id(), List.of())))
                .toList();
        WorkflowExecution.State state = new WorkflowExecution.State(
                e.getId(),
                e.getWorkflowId(),
                e.getWorkflowVersion(),
                e.getWorkflowName(),
                ExecutionStatus.valueOf(e.getStatus()),
                TriggerType.valueOf(e.getTriggerType()),
                e.getTriggeredBy(),
                e.getInput(),
                e.getOutput(),
                e.getErrorCode() == null ? null : new ExecutionError(e.getErrorCode(), e.getErrorMessage()),
                e.getIdempotencyKey(),
                e.getRetryOf(),
                e.getCreatedAt(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getDeadlineAt(),
                e.getEventSequence());
        return WorkflowExecution.restore(state, graph, nodeList, settings);
    }

    private static NodeExecution toDomain(NodeExecutionEntity n, List<NodeAttemptEntity> attempts) {
        return new NodeExecution(
                n.getNodeId(),
                n.getNodeType(),
                NodeStatus.valueOf(n.getStatus()),
                n.getAttemptCount(),
                n.getCurrentAttemptId(),
                n.getAvailableAt(),
                n.getLeaseExpiresAt(),
                n.getResumeAt(),
                n.getInput(),
                n.getOutput(),
                n.getBranch(),
                n.getErrorCode() == null
                        ? null
                        : new NodeFailure(
                                n.getErrorCode(), n.getErrorMessage(), Boolean.TRUE.equals(n.getErrorRetryable())),
                n.getStartedAt(),
                n.getFinishedAt(),
                attempts.stream()
                        .map(a -> new NodeAttempt(
                                a.getAttemptId(),
                                a.getAttemptNumber(),
                                NodeAttempt.Status.valueOf(a.getStatus()),
                                a.getDispatchedAt(),
                                a.getLeaseExpiresAt(),
                                a.getStartedAt(),
                                a.getFinishedAt(),
                                a.getWorkerId(),
                                a.getErrorCode(),
                                a.getErrorMessage()))
                        .toList());
    }

    private static void copyRoot(WorkflowExecution execution, WorkflowExecutionEntity e) {
        WorkflowExecution.State s = execution.state();
        e.setWorkflowId(s.workflowId());
        e.setWorkflowVersion(s.workflowVersion());
        e.setWorkflowName(s.workflowName());
        e.setStatus(s.status().name());
        e.setTriggerType(s.triggerType().name());
        e.setTriggeredBy(s.triggeredBy());
        e.setInput(s.input());
        e.setOutput(s.output());
        e.setErrorCode(s.error() == null ? null : s.error().code());
        e.setErrorMessage(s.error() == null ? null : truncate(s.error().message()));
        e.setIdempotencyKey(s.idempotencyKey());
        e.setRetryOf(s.retryOf());
        e.setCreatedAt(s.createdAt());
        e.setStartedAt(s.startedAt());
        e.setFinishedAt(s.finishedAt());
        e.setDeadlineAt(s.deadlineAt());
        e.setEventSequence(s.eventSequence());
    }

    private static void copyNode(NodeExecution n, NodeExecutionEntity e) {
        e.setStatus(n.status().name());
        e.setAttemptCount(n.attemptCount());
        e.setCurrentAttemptId(n.currentAttemptId());
        e.setAvailableAt(n.availableAt());
        e.setLeaseExpiresAt(n.leaseExpiresAt());
        e.setResumeAt(n.resumeAt());
        e.setInput(n.input());
        e.setOutput(n.output());
        e.setBranch(n.branch());
        e.setErrorCode(n.error() == null ? null : n.error().code());
        e.setErrorMessage(n.error() == null ? null : truncate(n.error().message()));
        e.setErrorRetryable(n.error() == null ? null : n.error().retryable());
        e.setStartedAt(n.startedAt());
        e.setFinishedAt(n.finishedAt());
    }

    private void syncAttempts(WorkflowExecution execution, Map<UUID, NodeAttemptEntity> existing) {
        for (NodeExecution node : execution.nodes()) {
            for (NodeAttempt attempt : node.attempts()) {
                NodeAttemptEntity entity = existing.get(attempt.attemptId());
                boolean isNew = entity == null;
                if (isNew) {
                    entity = new NodeAttemptEntity(
                            attempt.attemptId(),
                            execution.id(),
                            node.nodeId(),
                            attempt.number(),
                            attempt.dispatchedAt());
                }
                entity.setStatus(attempt.status().name());
                entity.setWorkerId(attempt.workerId());
                entity.setLeaseExpiresAt(attempt.leaseExpiresAt());
                entity.setStartedAt(attempt.startedAt());
                entity.setFinishedAt(attempt.finishedAt());
                entity.setErrorCode(attempt.errorCode());
                entity.setErrorMessage(truncate(attempt.errorMessage()));
                if (isNew) {
                    em.persist(entity); // insert state is captured at persist time, so populate first
                }
            }
        }
    }

    /** JDBC batch insert: history rows are append-only and written several at a time per transaction. */
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
