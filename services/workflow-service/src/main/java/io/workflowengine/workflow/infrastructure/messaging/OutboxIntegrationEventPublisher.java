package io.workflowengine.workflow.infrastructure.messaging;

import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.workflow.WorkflowAccessUpdated;
import io.workflowengine.contracts.workflow.WorkflowArchived;
import io.workflowengine.contracts.workflow.WorkflowVersionPublished;
import io.workflowengine.platform.outbox.OutboxWriter;
import io.workflowengine.workflow.application.port.out.IntegrationEventPublisher;
import io.workflowengine.workflow.domain.event.WorkflowDomainEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Translates domain events into versioned integration events written to the transactional outbox. */
@Component
public class OutboxIntegrationEventPublisher implements IntegrationEventPublisher {

    private final OutboxWriter outbox;

    public OutboxIntegrationEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(List<WorkflowDomainEvent> events) {
        for (WorkflowDomainEvent event : events) {
            String key = event.workflowId().toString();
            switch (event) {
                case WorkflowDomainEvent.AccessChanged e -> {
                    List<WorkflowAccessUpdated.AccessEntry> entries = new ArrayList<>();
                    entries.add(new WorkflowAccessUpdated.AccessEntry(e.ownerId(), "OWNER"));
                    e.permissions()
                            .forEach(p -> entries.add(new WorkflowAccessUpdated.AccessEntry(p.userId(), p.role().name())));
                    outbox.append(
                            Topics.WORKFLOW_EVENTS,
                            EventTypes.WORKFLOW_ACCESS_UPDATED,
                            key,
                            new WorkflowAccessUpdated(e.workflowId(), e.ownerId(), e.aclRevision(), entries, e.archived()));
                }
                case WorkflowDomainEvent.VersionPublished e -> outbox.append(
                        Topics.WORKFLOW_EVENTS,
                        EventTypes.WORKFLOW_VERSION_PUBLISHED,
                        key,
                        new WorkflowVersionPublished(
                                e.workflowId(),
                                e.version().version(),
                                e.version().name(),
                                e.ownerId(),
                                e.version().publishedBy(),
                                e.version().publishedAt(),
                                e.version().definition().document()));
                case WorkflowDomainEvent.Archived e -> outbox.append(
                        Topics.WORKFLOW_EVENTS,
                        EventTypes.WORKFLOW_ARCHIVED,
                        key,
                        new WorkflowArchived(e.workflowId(), e.archivedAt()));
            }
        }
    }
}
