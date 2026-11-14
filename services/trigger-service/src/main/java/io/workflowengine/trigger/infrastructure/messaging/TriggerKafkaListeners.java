package io.workflowengine.trigger.infrastructure.messaging;

import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.WorkflowExecutionLifecycleEvent;
import io.workflowengine.contracts.workflow.WorkflowAccessUpdated;
import io.workflowengine.contracts.workflow.WorkflowArchived;
import io.workflowengine.contracts.workflow.WorkflowVersionPublished;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.MessageContext;
import io.workflowengine.trigger.application.usecase.TriggerRegistrationService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TriggerKafkaListeners {

    private final EventCodec codec;
    private final TriggerRegistrationService registrations;

    public TriggerKafkaListeners(EventCodec codec, TriggerRegistrationService registrations) {
        this.codec = codec;
        this.registrations = registrations;
    }

    @KafkaListener(topics = Topics.WORKFLOW_EVENTS, groupId = "trigger-service")
    public void onWorkflowEvent(String value) {
        EventEnvelope envelope = codec.read(value);
        try (MessageContext.Scope ignored = MessageContext.open(envelope)) {
            switch (envelope.eventType()) {
                case EventTypes.WORKFLOW_VERSION_PUBLISHED -> {
                    WorkflowVersionPublished e = codec.payload(envelope, WorkflowVersionPublished.class);
                    registrations.onVersionPublished(envelope.eventId(), e.workflowId(), e.version(), e.definition());
                }
                case EventTypes.WORKFLOW_ARCHIVED ->
                    registrations.onArchived(
                            envelope.eventId(),
                            codec.payload(envelope, WorkflowArchived.class).workflowId());
                case EventTypes.WORKFLOW_ACCESS_UPDATED -> {
                    WorkflowAccessUpdated e = codec.payload(envelope, WorkflowAccessUpdated.class);
                    Map<String, String> roles = new HashMap<>();
                    e.entries().forEach(entry -> roles.put(entry.userId(), entry.role()));
                    registrations.onAccessUpdated(
                            envelope.eventId(), e.workflowId(), e.ownerId(), e.aclRevision(), roles);
                }
                default -> {
                    // not relevant for triggers
                }
            }
        }
    }

    @KafkaListener(topics = Topics.EXECUTION_LIFECYCLE, groupId = "trigger-service")
    public void onLifecycle(String value) {
        EventEnvelope envelope = codec.read(value);
        WorkflowExecutionLifecycleEvent e = codec.payload(envelope, WorkflowExecutionLifecycleEvent.class);
        try (MessageContext.Scope ignored = MessageContext.open(envelope)) {
            registrations.onExecutionStatus(envelope.eventId(), e.executionId(), e.status());
        }
    }
}
