package io.workflowengine.execution.domain.model;

public record NodeFailure(String code, String message, boolean retryable) {

    public static final String LEASE_EXPIRED = "TASK_LEASE_EXPIRED";

    public static NodeFailure leaseExpired() {
        return new NodeFailure(LEASE_EXPIRED, "No result was received before the task lease expired", true);
    }
}
