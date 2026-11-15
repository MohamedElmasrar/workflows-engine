package io.workflowengine.execution.infrastructure.persistence;

import io.workflowengine.execution.application.port.out.TransactionRunner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** New transaction per call; optimistic-lock conflicts are retried with a small jittered pause. */
@Component
@Slf4j
@RequiredArgsConstructor
public class RetryingTransactionRunner implements TransactionRunner {
    static final int MAX_ATTEMPTS = 5;

    private final TransactionTemplate template;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.debug("Concurrent modification detected (attempt {}), retrying", attempt);
                pause(attempt);
            }
        }
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 20L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
