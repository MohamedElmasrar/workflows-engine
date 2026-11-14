package io.workflowengine.execution.application.port.out;

import io.workflowengine.execution.domain.model.WorkflowExecution;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository {

    /** Loads the aggregate for modification; the root version is incremented on commit (optimistic lock). */
    Optional<WorkflowExecution> loadForUpdate(UUID id);

    /** Loads the aggregate for reading. */
    Optional<WorkflowExecution> find(UUID id);

    boolean exists(UUID id);

    Optional<UUID> findIdByIdempotencyKey(UUID workflowId, String idempotencyKey);

    void insert(WorkflowExecution execution);

    void update(WorkflowExecution execution);

    long countRunning(UUID workflowId);

    /** Executions with a timer that is due: retry back-off, delay, task lease or execution deadline. */
    List<UUID> findDue(Instant now, int limit);

    /** PENDING executions waiting for a concurrency slot, oldest first. */
    List<UUID> findPending(int limit);

    long countActive();
}
