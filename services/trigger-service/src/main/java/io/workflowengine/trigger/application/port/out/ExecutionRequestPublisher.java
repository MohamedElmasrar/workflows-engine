package io.workflowengine.trigger.application.port.out;

import io.workflowengine.contracts.execution.WorkflowExecutionRequested;

/** Transactional (outbox) publication of execution requests. */
public interface ExecutionRequestPublisher {

    void publish(WorkflowExecutionRequested request);
}
