package io.workflowengine.platform.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.messaging")
public class MessagingProperties {

    private Duration retryInitialInterval = Duration.ofMillis(500);
    private double retryMultiplier = 2.0;
    private Duration retryMaxInterval = Duration.ofSeconds(10);
    private Duration retryMaxElapsed = Duration.ofSeconds(60);
    private int partitions = 3;
    private short replicationFactor = 1;

    public Duration getRetryInitialInterval() {
        return retryInitialInterval;
    }

    public void setRetryInitialInterval(Duration retryInitialInterval) {
        this.retryInitialInterval = retryInitialInterval;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public Duration getRetryMaxInterval() {
        return retryMaxInterval;
    }

    public void setRetryMaxInterval(Duration retryMaxInterval) {
        this.retryMaxInterval = retryMaxInterval;
    }

    public Duration getRetryMaxElapsed() {
        return retryMaxElapsed;
    }

    public void setRetryMaxElapsed(Duration retryMaxElapsed) {
        this.retryMaxElapsed = retryMaxElapsed;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public short getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(short replicationFactor) {
        this.replicationFactor = replicationFactor;
    }
}
