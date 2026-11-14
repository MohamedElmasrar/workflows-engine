package io.workflowengine.execution.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** Node execution state machine; see docs/architecture/overview.md section 5.2. */
public enum NodeStatus {
    PENDING,
    READY,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED,
    TIMED_OUT;

    public Set<NodeStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(READY, SKIPPED, CANCELLED);
            case READY -> EnumSet.of(QUEUED, RUNNING, CANCELLED);
            case QUEUED -> EnumSet.of(RUNNING, COMPLETED, READY, FAILED, CANCELLED, TIMED_OUT);
            case RUNNING -> EnumSet.of(COMPLETED, READY, FAILED, CANCELLED, TIMED_OUT);
            case COMPLETED, FAILED, SKIPPED, CANCELLED, TIMED_OUT -> EnumSet.noneOf(NodeStatus.class);
        };
    }

    public boolean canTransitionTo(NodeStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }

    /** A task has been handed to a worker and its result is awaited. */
    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }
}
