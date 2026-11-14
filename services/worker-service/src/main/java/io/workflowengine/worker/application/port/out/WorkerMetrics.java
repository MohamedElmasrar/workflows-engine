package io.workflowengine.worker.application.port.out;

import java.time.Duration;

public interface WorkerMetrics {

    void taskProcessed(String nodeType, String outcome, Duration duration);

    void cachedResultReplayed(String nodeType);
}
