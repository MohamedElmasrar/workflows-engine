package io.workflowengine.execution.application.port.out;

import java.util.function.Supplier;

/**
 * Runs work in a new transaction, re-running it when the optimistic lock on an execution detects a concurrent
 * modification. The work must therefore be free of non-transactional side effects.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
