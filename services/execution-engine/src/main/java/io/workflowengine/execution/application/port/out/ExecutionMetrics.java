package io.workflowengine.execution.application.port.out;

import io.workflowengine.execution.domain.model.ExecutionEffect;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import java.time.Duration;
import java.util.List;

public interface ExecutionMetrics {

    void recordEffects(WorkflowExecution execution, List<ExecutionEffect> effects);

    void staleResult(String kind);

    void duplicateMessage(String consumer);

    void queueLatency(String nodeType, Duration latency);
}
