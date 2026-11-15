package io.workflowengine.worker.infrastructure.messaging;

import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.MessageContext;
import io.workflowengine.worker.application.usecase.TaskProcessor;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;

@RequiredArgsConstructor
public class TaskListener {

    private final EventCodec codec;
    private final TaskProcessor processor;

    @KafkaListener(topics = Topics.NODE_TASKS, groupId = "worker-service", concurrency = "${worker.concurrency:3}")
    public void onTask(String value) {
        EventEnvelope envelope = codec.read(value);
        if (!EventTypes.NODE_EXECUTION_SCHEDULED.equals(envelope.eventType())) {
            return;
        }
        NodeExecutionScheduled task = codec.payload(envelope, NodeExecutionScheduled.class);
        try (MessageContext.Scope ignored = MessageContext.open(envelope)) {
            MDC.put("executionId", task.executionId().toString());
            MDC.put("nodeId", task.nodeId());
            processor.process(task);
        } finally {
            MDC.remove("executionId");
            MDC.remove("nodeId");
        }
    }
}
