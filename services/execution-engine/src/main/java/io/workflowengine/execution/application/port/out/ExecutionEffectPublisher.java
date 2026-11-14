package io.workflowengine.execution.application.port.out;

import io.workflowengine.execution.domain.model.ExecutionEffect;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import java.util.List;

/** Publishes effects (worker tasks, lifecycle events). Must be transactional with the state change (outbox). */
public interface ExecutionEffectPublisher {

    void publish(WorkflowExecution execution, List<ExecutionEffect> effects);
}
