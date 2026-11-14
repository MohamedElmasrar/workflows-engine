package io.workflowengine.trigger.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository {

    record Delivery(
            UUID id,
            UUID workflowId,
            String triggerType,
            String dedupeKey,
            UUID executionId,
            String executionStatus,
            int payloadBytes,
            Instant receivedAt) {}

    /** Inserts unless the dedupe key exists; returns the stored delivery (new or existing) and whether it was new. */
    record InsertResult(Delivery delivery, boolean created) {}

    InsertResult insertIfAbsent(Delivery delivery);

    List<Delivery> recent(UUID workflowId, int limit);

    void updateExecutionStatus(UUID executionId, String status);

    Optional<Delivery> byExecution(UUID executionId);
}
