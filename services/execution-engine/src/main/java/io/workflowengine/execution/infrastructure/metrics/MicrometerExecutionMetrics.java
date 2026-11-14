package io.workflowengine.execution.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.workflowengine.execution.application.port.out.ExecutionMetrics;
import io.workflowengine.execution.domain.model.ExecutionEffect;
import io.workflowengine.execution.domain.model.ExecutionStatus;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Execution metrics. Recorded after commit so transactions re-run on lock conflicts are not double counted. Labels are
 * bounded (status, trigger type, node type); execution ids are never used as labels.
 */
@Component
public class MicrometerExecutionMetrics implements ExecutionMetrics {

    private final MeterRegistry registry;

    public MicrometerExecutionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordEffects(WorkflowExecution execution, List<ExecutionEffect> effects) {
        String trigger = execution.triggerType().name();
        Instant startedAt = execution.startedAt();
        afterCommit(() -> {
            for (ExecutionEffect effect : effects) {
                switch (effect) {
                    case ExecutionEffect.LifecycleChanged l when l.status() == ExecutionStatus.RUNNING ->
                            Counter.builder("workflow.executions.started").tag("trigger", trigger).register(registry).increment();
                    case ExecutionEffect.LifecycleChanged l -> {
                        Counter.builder("workflow.executions.finished")
                                .tag("status", l.status().name())
                                .tag("trigger", trigger)
                                .register(registry)
                                .increment();
                        if (startedAt != null) {
                            Timer.builder("workflow.execution.duration")
                                    .tag("status", l.status().name())
                                    .publishPercentileHistogram()
                                    .register(registry)
                                    .record(Duration.between(startedAt, Instant.now()));
                        }
                    }
                    case ExecutionEffect.DispatchNode d -> Counter.builder("workflow.node.tasks.dispatched")
                            .tag("nodeType", d.task().nodeType().name())
                            .register(registry)
                            .increment();
                    case ExecutionEffect.NodeAttemptFinished f -> {
                        Timer.builder("workflow.node.duration")
                                .tag("nodeType", f.nodeType())
                                .tag("outcome", f.willRetry() ? "RETRY" : f.outcome().name())
                                .publishPercentileHistogram()
                                .register(registry)
                                .record(Duration.ofMillis(f.durationMs()));
                        if (f.willRetry()) {
                            Counter.builder("workflow.node.retries").tag("nodeType", f.nodeType()).register(registry).increment();
                        }
                    }
                }
            }
        });
    }

    @Override
    public void staleResult(String kind) {
        Counter.builder("workflow.results.stale").tag("kind", kind).register(registry).increment();
    }

    @Override
    public void duplicateMessage(String consumer) {
        afterCommit(() -> Counter.builder("workflow.messages.duplicate").tag("consumer", consumer).register(registry).increment());
    }

    @Override
    public void queueLatency(String nodeType, Duration latency) {
        afterCommit(() -> Timer.builder("workflow.task.queue.latency")
                .tag("nodeType", nodeType)
                .publishPercentileHistogram()
                .register(registry)
                .record(latency));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
