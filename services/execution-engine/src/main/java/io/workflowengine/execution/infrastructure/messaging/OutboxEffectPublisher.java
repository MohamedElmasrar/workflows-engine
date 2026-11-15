package io.workflowengine.execution.infrastructure.messaging;

import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.contracts.execution.WorkflowExecutionLifecycleEvent;
import io.workflowengine.execution.application.port.out.ExecutionEffectPublisher;
import io.workflowengine.execution.domain.model.ExecutionEffect;
import io.workflowengine.execution.domain.model.ExecutionStatus;
import io.workflowengine.execution.domain.model.NodeTask;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import io.workflowengine.platform.outbox.OutboxWriter;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Writes worker tasks and lifecycle events to the outbox in the same transaction as the state change. */
@Component
@RequiredArgsConstructor
public class OutboxEffectPublisher implements ExecutionEffectPublisher {

    private final OutboxWriter outbox;

    @Override
    public void publish(WorkflowExecution execution, List<ExecutionEffect> effects) {
        String key = execution.id().toString();
        for (ExecutionEffect effect : effects) {
            switch (effect) {
                case ExecutionEffect.DispatchNode dispatch -> {
                    NodeTask t = dispatch.task();
                    // keyed by execution and node: parallel branches spread over partitions (and workers), while all
                    // attempts of one node stay ordered on one partition
                    outbox.append(
                            Topics.NODE_TASKS,
                            EventTypes.NODE_EXECUTION_SCHEDULED,
                            key,
                            key + ":" + t.nodeId(),
                            new NodeExecutionScheduled(
                                    t.executionId(),
                                    t.workflowId(),
                                    t.nodeId(),
                                    t.nodeType().name(),
                                    t.attemptId(),
                                    t.attemptNumber(),
                                    t.timeoutMs(),
                                    t.config()));
                }
                case ExecutionEffect.LifecycleChanged lifecycle ->
                    outbox.append(
                            Topics.EXECUTION_LIFECYCLE,
                            eventType(lifecycle.status()),
                            key,
                            new WorkflowExecutionLifecycleEvent(
                                    execution.id(),
                                    execution.workflowId(),
                                    execution.workflowVersion(),
                                    lifecycle.status().name(),
                                    Instant.now(),
                                    lifecycle.error() == null
                                            ? null
                                            : new WorkflowExecutionLifecycleEvent.ExecutionError(
                                                    lifecycle.error().code(),
                                                    lifecycle.error().message())));
                case ExecutionEffect.NodeAttemptFinished ignored -> {
                    // metrics only
                }
            }
        }
    }

    private static String eventType(ExecutionStatus status) {
        return switch (status) {
            case RUNNING -> EventTypes.WORKFLOW_EXECUTION_STARTED;
            case COMPLETED -> EventTypes.WORKFLOW_EXECUTION_COMPLETED;
            case FAILED -> EventTypes.WORKFLOW_EXECUTION_FAILED;
            case CANCELLED -> EventTypes.WORKFLOW_EXECUTION_CANCELLED;
            case TIMED_OUT -> EventTypes.WORKFLOW_EXECUTION_TIMED_OUT;
            case PENDING -> throw new IllegalArgumentException("PENDING is not a lifecycle event");
        };
    }
}
