package io.workflowengine.platform.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.outbox")
public class OutboxProperties {

    /** Whether this instance runs the polling publisher. */
    private boolean publisherEnabled = true;

    private int batchSize = 100;
    private Duration sendTimeout = Duration.ofSeconds(10);
    /** How long published outbox rows and processed-message records are kept. */
    private Duration retention = Duration.ofDays(7);

    public boolean isPublisherEnabled() {
        return publisherEnabled;
    }

    public void setPublisherEnabled(boolean publisherEnabled) {
        this.publisherEnabled = publisherEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }
}
