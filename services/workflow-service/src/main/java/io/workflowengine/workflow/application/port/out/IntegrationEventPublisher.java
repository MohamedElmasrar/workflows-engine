package io.workflowengine.workflow.application.port.out;

import io.workflowengine.workflow.domain.event.WorkflowDomainEvent;
import java.util.List;

/** Publishes domain events to other services. Implementations must be transactional (outbox). */
public interface IntegrationEventPublisher {

    void publish(List<WorkflowDomainEvent> events);
}
