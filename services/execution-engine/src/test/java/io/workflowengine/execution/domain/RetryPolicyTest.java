package io.workflowengine.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.workflowengine.execution.domain.model.ExponentialBackoffRetryPolicy;
import io.workflowengine.execution.domain.model.NodeFailure;
import io.workflowengine.execution.domain.model.RetryPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy =
            new ExponentialBackoffRetryPolicy(4, Duration.ofSeconds(1), 3.0, Duration.ofSeconds(5));

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertThat(policy.backoffAfter(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoffAfter(2)).isEqualTo(Duration.ofSeconds(3));
        assertThat(policy.backoffAfter(3)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void onlyRetryableFailuresWithinBudgetAreRetried() {
        NodeFailure retryable = new NodeFailure("X", "x", true);
        assertThat(policy.shouldRetry(3, retryable)).isTrue();
        assertThat(policy.shouldRetry(4, retryable)).isFalse();
        assertThat(policy.shouldRetry(1, new NodeFailure("X", "x", false))).isFalse();
        assertThat(RetryPolicy.none().shouldRetry(1, retryable)).isFalse();
    }
}
