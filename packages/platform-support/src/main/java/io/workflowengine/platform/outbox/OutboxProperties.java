package io.workflowengine.platform.outbox;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("platform.outbox")
public class OutboxProperties {

    /** Whether this instance runs the polling publisher. */
    private boolean publisherEnabled = true;

    private int batchSize = 100;
    private Duration sendTimeout = Duration.ofSeconds(10);
    /** How long published outbox rows and processed-message records are kept. */
    private Duration retention = Duration.ofDays(7);
}
