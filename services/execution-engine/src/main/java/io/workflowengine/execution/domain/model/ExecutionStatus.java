package io.workflowengine.execution.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** Workflow execution state machine. Allowed transitions are declared here and enforced by the aggregate. */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    public Set<ExecutionStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> EnumSet.of(RUNNING, CANCELLED, FAILED);
            case RUNNING -> EnumSet.of(COMPLETED, FAILED, CANCELLED, TIMED_OUT);
            case COMPLETED, FAILED, CANCELLED, TIMED_OUT -> EnumSet.noneOf(ExecutionStatus.class);
        };
    }

    public boolean canTransitionTo(ExecutionStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }
}
