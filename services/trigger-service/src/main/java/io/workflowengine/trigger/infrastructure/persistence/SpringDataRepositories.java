package io.workflowengine.trigger.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WebhookRegistrationJpaRepository extends JpaRepository<WebhookRegistrationEntity, UUID> {

    Optional<WebhookRegistrationEntity> findByTokenHash(String tokenHash);

    Optional<WebhookRegistrationEntity> findByWorkflowId(UUID workflowId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update WebhookRegistrationEntity w set w.active = false, w.updatedAt = :now where w.workflowId = :workflowId")
    int deactivate(@Param("workflowId") UUID workflowId, @Param("now") Instant now);
}

interface ScheduledTriggerJpaRepository extends JpaRepository<ScheduledTriggerEntity, UUID> {

    Optional<ScheduledTriggerEntity> findByWorkflowId(UUID workflowId);

    /**
     * Native on purpose: {@code FOR UPDATE SKIP LOCKED} lets several trigger-service replicas poll concurrently, each
     * locking a disjoint set of due schedules. JPQL has no portable way to express SKIP LOCKED.
     */
    @Query(
            value = "select * from scheduled_trigger where active and next_fire_at <= :now "
                    + "order by next_fire_at limit :limit for update skip locked",
            nativeQuery = true)
    List<ScheduledTriggerEntity> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ScheduledTriggerEntity s set s.active = false, s.updatedAt = :now where s.workflowId = :workflowId")
    int deactivate(@Param("workflowId") UUID workflowId, @Param("now") Instant now);
}

interface TriggerDeliveryJpaRepository extends JpaRepository<TriggerDeliveryEntity, UUID> {

    Optional<TriggerDeliveryEntity> findByDedupeKey(String dedupeKey);

    Optional<TriggerDeliveryEntity> findByExecutionId(UUID executionId);

    List<TriggerDeliveryEntity> findByWorkflowIdOrderByReceivedAtDesc(UUID workflowId, Limit limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TriggerDeliveryEntity d set d.executionStatus = :status where d.executionId = :executionId")
    int updateExecutionStatus(@Param("executionId") UUID executionId, @Param("status") String status);
}

interface WorkflowAccessJpaRepository extends JpaRepository<WorkflowAccessEntity, WorkflowAccessEntity.Key> {}
