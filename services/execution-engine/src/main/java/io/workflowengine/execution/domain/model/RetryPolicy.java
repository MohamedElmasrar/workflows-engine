package io.workflowengine.execution.domain.model;

import java.time.Duration;

/** Strategy deciding whether and when a failed node attempt is retried. */
public interface RetryPolicy {

    int maxAttempts();

    /** Whether another attempt should follow the given (1-based) attempt that failed with {@code failure}. */
    default boolean shouldRetry(int failedAttempt, NodeFailure failure) {
        return failure.retryable() && failedAttempt < maxAttempts();
    }

    /** Delay before the attempt following {@code failedAttempt}. */
    Duration backoffAfter(int failedAttempt);

    static RetryPolicy none() {
        return new ExponentialBackoffRetryPolicy(1, Duration.ZERO, 1, Duration.ZERO);
    }
}
