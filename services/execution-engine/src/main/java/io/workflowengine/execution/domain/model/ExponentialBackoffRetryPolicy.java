package io.workflowengine.execution.domain.model;

import java.time.Duration;

/** delay(n) = min(initialInterval * multiplier^(n-1), maxInterval). */
public record ExponentialBackoffRetryPolicy(
        int maxAttempts, Duration initialInterval, double multiplier, Duration maxInterval) implements RetryPolicy {

    public static final ExponentialBackoffRetryPolicy DEFAULT =
            new ExponentialBackoffRetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(60));

    public ExponentialBackoffRetryPolicy {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 10");
        }
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be >= 1");
        }
    }

    @Override
    public Duration backoffAfter(int failedAttempt) {
        double millis = initialInterval.toMillis() * Math.pow(multiplier, Math.max(0, failedAttempt - 1));
        return Duration.ofMillis((long) Math.min(millis, maxInterval.toMillis()));
    }
}
