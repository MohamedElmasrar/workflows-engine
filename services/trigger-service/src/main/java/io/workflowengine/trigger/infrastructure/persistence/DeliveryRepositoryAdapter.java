package io.workflowengine.trigger.infrastructure.persistence;

import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeliveryRepositoryAdapter implements DeliveryRepository {

    private final TriggerDeliveryJpaRepository deliveries;
    private final JdbcTemplate jdbc;

    /**
     * SQL on purpose: {@code ON CONFLICT (dedupe_key) DO NOTHING} decides atomically which of several concurrent
     * deliveries (webhook retries, two scheduler replicas) is the first. Check-then-insert through JPA would race.
     */
    @Override
    public InsertResult insertIfAbsent(Delivery d) {
        int inserted = jdbc.update(
                """
                insert into trigger_delivery (id, workflow_id, trigger_type, dedupe_key, execution_id, execution_status,
                    payload_bytes, received_at)
                values (?, ?, ?, ?, ?, ?, ?, ?) on conflict (dedupe_key) do nothing
                """,
                d.id(),
                d.workflowId(),
                d.triggerType(),
                d.dedupeKey(),
                d.executionId(),
                d.executionStatus(),
                d.payloadBytes(),
                Timestamp.from(d.receivedAt()));
        if (inserted == 1) {
            return new InsertResult(d, true);
        }
        return new InsertResult(
                deliveries
                        .findByDedupeKey(d.dedupeKey())
                        .map(DeliveryRepositoryAdapter::toDomain)
                        .orElseThrow(),
                false);
    }

    @Override
    public List<Delivery> recent(UUID workflowId, int limit) {
        return deliveries.findByWorkflowIdOrderByReceivedAtDesc(workflowId, Limit.of(limit)).stream()
                .map(DeliveryRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public void updateExecutionStatus(UUID executionId, String status) {
        deliveries.updateExecutionStatus(executionId, status);
    }

    @Override
    public Optional<Delivery> byExecution(UUID executionId) {
        return deliveries.findByExecutionId(executionId).map(DeliveryRepositoryAdapter::toDomain);
    }

    private static Delivery toDomain(TriggerDeliveryEntity e) {
        return new Delivery(
                e.getId(),
                e.getWorkflowId(),
                e.getTriggerType(),
                e.getDedupeKey(),
                e.getExecutionId(),
                e.getExecutionStatus(),
                e.getPayloadBytes(),
                e.getReceivedAt());
    }
}
