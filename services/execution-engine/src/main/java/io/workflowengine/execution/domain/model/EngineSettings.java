package io.workflowengine.execution.domain.model;

import java.time.Duration;

/**
 * Engine-wide policy values.
 *
 * @param leaseGrace extra time added to a node timeout before an unanswered task is considered lost
 */
public record EngineSettings(Duration leaseGrace) {

    public static final EngineSettings DEFAULT = new EngineSettings(Duration.ofSeconds(60));
}
