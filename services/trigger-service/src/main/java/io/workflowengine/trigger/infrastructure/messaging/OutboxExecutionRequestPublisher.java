package io.workflowengine.trigger.infrastructure.messaging;

import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.WorkflowExecutionRequested;
import io.workflowengine.platform.outbox.OutboxWriter;
import io.workflowengine.trigger.application.port.out.ExecutionRequestPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxExecutionRequestPublisher implements ExecutionRequestPublisher {

    private final OutboxWriter outbox;

    @Override
    public void publish(WorkflowExecutionRequested request) {
        outbox.append(
                Topics.EXECUTION_REQUESTS,
                EventTypes.WORKFLOW_EXECUTION_REQUESTED,
                request.workflowId().toString(),
                request);
    }
}
