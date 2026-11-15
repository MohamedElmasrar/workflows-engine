package io.workflowengine.platform.messaging;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("platform.messaging")
public class MessagingProperties {

    private Duration retryInitialInterval = Duration.ofMillis(500);
    private double retryMultiplier = 2.0;
    private Duration retryMaxInterval = Duration.ofSeconds(10);
    private Duration retryMaxElapsed = Duration.ofSeconds(60);
    private int partitions = 3;
    private short replicationFactor = 1;
}
