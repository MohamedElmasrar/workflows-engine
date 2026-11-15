package io.workflowengine.execution.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkflowExecutionJpaRepository extends JpaRepository<WorkflowExecutionEntity, UUID> {

    /** Loads the aggregate root and increments its version on commit, so concurrent changes to any node conflict. */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<WorkflowExecutionEntity> findForUpdateById(UUID id);

    long countByWorkflowIdAndStatus(UUID workflowId, String status);

    long countByStatusIn(Collection<String> statuses);

    @Query("select e.id from WorkflowExecutionEntity e where e.workflowId = :workflowId and e.idempotencyKey = :key")
    Optional<UUID> findIdByIdempotencyKey(@Param("workflowId") UUID workflowId, @Param("key") String key);

    @Query("select e.id from WorkflowExecutionEntity e where e.status = :status order by e.createdAt")
    List<UUID> findIdsByStatusOldestFirst(@Param("status") String status, Pageable page);

    @Query("select e.status from WorkflowExecutionEntity e where e.id = :id")
    Optional<String> findStatusById(@Param("id") UUID id);
}

interface NodeExecutionJpaRepository extends JpaRepository<NodeExecutionEntity, NodeExecutionEntity.Key> {
    List<NodeExecutionEntity> findByExecutionId(UUID executionId);
}

interface NodeAttemptJpaRepository extends JpaRepository<NodeAttemptEntity, UUID> {
    List<NodeAttemptEntity> findByExecutionIdOrderByAttemptNumber(UUID executionId);
}

interface ExecutionSummaryJpaRepository
        extends JpaRepository<ExecutionSummaryEntity, UUID>, JpaSpecificationExecutor<ExecutionSummaryEntity> {}

interface ExecutionEventJpaRepository extends JpaRepository<ExecutionEventEntity, Long> {
    List<ExecutionEventEntity> findByExecutionIdAndSequenceGreaterThanOrderBySequence(
            UUID executionId, long afterSequence, Limit limit);
}

interface WorkflowProjectionJpaRepository extends JpaRepository<WorkflowProjectionEntity, UUID> {

    /** SELECT ... FOR UPDATE: serializes execution starts of one workflow across engine replicas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowProjectionEntity> findLockedByWorkflowId(UUID workflowId);

    @Modifying(clearAutomatically = true)
    @Query("update WorkflowProjectionEntity p set p.archived = true, p.updatedAt = :now where p.workflowId = :id")
    int markArchived(@Param("id") UUID workflowId, @Param("now") Instant now);
}

interface WorkflowAccessJpaRepository extends JpaRepository<WorkflowAccessEntity, WorkflowAccessEntity.Key> {
    List<WorkflowAccessEntity> findByWorkflowId(UUID workflowId);
}

interface WorkflowVersionSnapshotJpaRepository
        extends JpaRepository<WorkflowVersionSnapshotEntity, WorkflowVersionSnapshotEntity.Key> {
    Optional<WorkflowVersionSnapshotEntity> findFirstByWorkflowIdOrderByVersionDesc(UUID workflowId);
}
