package io.workflowengine.execution.domain.model;

/** Side effects requested by the aggregate, published by the application layer through the outbox. */
public sealed interface ExecutionEffect {

    record DispatchNode(NodeTask task) implements ExecutionEffect {}

    record LifecycleChanged(ExecutionStatus status, ExecutionError error) implements ExecutionEffect {}

    /** Emitted when a node attempt finishes, for metrics. */
    record NodeAttemptFinished(String nodeType, NodeStatus outcome, long durationMs, boolean willRetry)
            implements ExecutionEffect {}
}
