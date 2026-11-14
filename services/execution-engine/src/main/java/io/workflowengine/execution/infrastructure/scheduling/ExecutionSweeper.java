package io.workflowengine.execution.infrastructure.scheduling;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.workflowengine.execution.application.port.out.ExecutionRepository;
import io.workflowengine.execution.application.usecase.ExecutionService;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Durable timers and recovery. Runs on every replica; overlapping work on the same execution is resolved by the
 * optimistic lock. Because all timers are database columns, a restarted engine resumes exactly where it stopped.
 */
@Component
@ConditionalOnProperty(value = "engine.sweeper.enabled", matchIfMissing = true)
public class ExecutionSweeper {

    private static final Logger log = LoggerFactory.getLogger(ExecutionSweeper.class);
    private static final int BATCH = 100;

    private final ExecutionRepository repository;
    private final ExecutionService service;
    private final Clock clock;
    private final AtomicLong active = new AtomicLong();

    public ExecutionSweeper(ExecutionRepository repository, ExecutionService service, Clock clock, MeterRegistry registry) {
        this.repository = repository;
        this.service = service;
        this.clock = clock;
        Gauge.builder("workflow.executions.active", active, AtomicLong::get)
                .description("PENDING + RUNNING executions")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${engine.sweeper.interval:1s}")
    public void sweep() {
        for (UUID id : repository.findDue(clock.instant(), BATCH)) {
            run(() -> service.advance(id), id);
        }
        for (UUID id : repository.findPending(BATCH)) {
            run(() -> service.startPending(id), id);
        }
        active.set(repository.countActive());
    }

    private static void run(Runnable task, UUID executionId) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("Sweeper could not advance execution {}: {}", executionId, e.getMessage());
        }
    }
}
