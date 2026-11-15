package io.workflowengine.worker.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.workflowengine.worker.application.port.out.WorkerMetrics;
import java.time.Duration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MicrometerWorkerMetrics implements WorkerMetrics {

    private final MeterRegistry registry;

    @Override
    public void taskProcessed(String nodeType, String outcome, Duration duration) {
        Timer.builder("worker.task.duration")
                .tag("nodeType", nodeType)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    @Override
    public void cachedResultReplayed(String nodeType) {
        Counter.builder("worker.tasks.replayed")
                .tag("nodeType", nodeType)
                .register(registry)
                .increment();
    }
}
