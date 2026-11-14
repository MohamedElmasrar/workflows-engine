package io.workflowengine.execution.infrastructure.messaging;

import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.NodeExecutionCompleted;
import io.workflowengine.contracts.execution.NodeExecutionFailed;
import io.workflowengine.contracts.execution.NodeExecutionStarted;
import io.workflowengine.contracts.execution.WorkflowExecutionRequested;
import io.workflowengine.contracts.workflow.WorkflowAccessUpdated;
import io.workflowengine.contracts.workflow.WorkflowArchived;
import io.workflowengine.contracts.workflow.WorkflowVersionPublished;
import io.workflowengine.execution.application.command.ExecutionCommands.StartExecution;
import io.workflowengine.execution.application.usecase.ExecutionService;
import io.workflowengine.execution.application.usecase.WorkflowProjectionService;
import io.workflowengine.execution.domain.exception.ExecutionStateException;
import io.workflowengine.execution.domain.model.NodeFailure;
import io.workflowengine.execution.domain.model.PublishedVersion;
import io.workflowengine.execution.domain.model.TriggerType;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.MessageContext;
import io.workflowengine.platform.messaging.NonRetryableMessageException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry points. Each listener parses the envelope, opens the correlation scope and delegates to a use case.
 * Exceptions propagate to the container error handler (back-off, then dead-letter topic).
 */
@Component
public class EngineKafkaListeners {

    private static final Logger log = LoggerFactory.getLogger(EngineKafkaListeners.class);

    private final EventCodec codec;
    private final ExecutionService executions;
    private final WorkflowProjectionService projections;

    public EngineKafkaListeners(EventCodec codec, ExecutionService executions, WorkflowProjectionService projections) {
        this.codec = codec;
        this.executions = executions;
        this.projections = projections;
    }

    @KafkaListener(topics = Topics.WORKFLOW_EVENTS, groupId = "execution-engine")
    public void onWorkflowEvent(String value) {
        EventEnvelope envelope = codec.read(value);
        try (MessageContext.Scope ignored = MessageContext.open(envelope)) {
            switch (envelope.eventType()) {
                case EventTypes.WORKFLOW_ACCESS_UPDATED -> {
                    WorkflowAccessUpdated e = codec.payload(envelope, WorkflowAccessUpdated.class);
                    Map<String, String> roles = new HashMap<>();
                    e.entries().forEach(entry -> roles.put(entry.userId(), entry.role()));
                    projections.accessUpdated(
                            envelope.eventId(), e.workflowId(), e.ownerId(), e.aclRevision(), e.archived(), roles);
                }
                case EventTypes.WORKFLOW_VERSION_PUBLISHED -> {
                    WorkflowVersionPublished e = codec.payload(envelope, WorkflowVersionPublished.class);
                    projections.versionPublished(
                            envelope.eventId(),
                            new PublishedVersion(e.workflowId(), e.version(), e.name(), e.definition()),
                            e.ownerId(),
                            e.publishedAt());
                }
                case EventTypes.WORKFLOW_ARCHIVED ->
                    projections.archived(
                            envelope.eventId(),
                            codec.payload(envelope, WorkflowArchived.class).workflowId());
                default -> log.debug("Ignoring workflow event type {}", envelope.eventType());
            }
        }
    }

    @KafkaListener(topics = Topics.EXECUTION_REQUESTS, groupId = "execution-engine")
    public void onExecutionRequested(String value) {
        EventEnvelope envelope = codec.read(value);
        if (!EventTypes.WORKFLOW_EXECUTION_REQUESTED.equals(envelope.eventType())) {
            return;
        }
        WorkflowExecutionRequested r = codec.payload(envelope, WorkflowExecutionRequested.class);
        try (MessageContext.Scope ignored = MessageContext.open(envelope)) {
            MDC.put("executionId", r.executionId().toString());
            TriggerType type;
            try {
                type = TriggerType.valueOf(r.triggerType());
            } catch (IllegalArgumentException e) {
                throw new NonRetryableMessageException("Unknown trigger type " + r.triggerType());
            }
            executions.start(
                    new StartExecution(
                            r.executionId(),
                            r.workflowId(),
                            r.workflowVersion(),
                            r.input(),
                            type,
                            r.triggeredBy(),
                            r.idempotencyKey(),
                            null),
                    envelope.eventId());
        } catch (ExecutionStateException | IllegalArgumentException e) {
            throw new NonRetryableMessageException("Execution request rejected: " + e.getMessage(), e);
        } finally {
            MDC.remove("executionId");
        }
        // VersionNotAvailableException is retryable: the version projection may still be in flight.
    }

    @KafkaListener(topics = Topics.NODE_RESULTS, groupId = "execution-engine")
    public void onNodeResult(String value) {
        EventEnvelope envelope = codec.read(value);
        try (MessageContext.Scope ignored = MessageContext.open(envelope)) {
            MDC.put("executionId", envelope.aggregateId());
            switch (envelope.eventType()) {
                case EventTypes.NODE_EXECUTION_STARTED -> {
                    NodeExecutionStarted e = codec.payload(envelope, NodeExecutionStarted.class);
                    executions.nodeStarted(
                            envelope.eventId(), e.executionId(), e.nodeId(), e.attemptId(), e.workerId());
                }
                case EventTypes.NODE_EXECUTION_COMPLETED -> {
                    NodeExecutionCompleted e = codec.payload(envelope, NodeExecutionCompleted.class);
                    executions.nodeCompleted(
                            envelope.eventId(),
                            e.executionId(),
                            e.nodeId(),
                            e.attemptId(),
                            e.output(),
                            e.branch(),
                            e.workerId());
                }
                case EventTypes.NODE_EXECUTION_FAILED -> {
                    NodeExecutionFailed e = codec.payload(envelope, NodeExecutionFailed.class);
                    executions.nodeFailed(
                            envelope.eventId(),
                            e.executionId(),
                            e.nodeId(),
                            e.attemptId(),
                            new NodeFailure(
                                    e.error().code(),
                                    e.error().message(),
                                    e.error().retryable()),
                            e.workerId());
                }
                default -> log.debug("Ignoring node result type {}", envelope.eventType());
            }
        } finally {
            MDC.remove("executionId");
        }
    }
}
