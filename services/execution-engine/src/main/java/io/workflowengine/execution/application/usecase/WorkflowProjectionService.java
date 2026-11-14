package io.workflowengine.execution.application.usecase;

import io.workflowengine.execution.application.port.out.MessageDeduplicator;
import io.workflowengine.execution.application.port.out.TransactionRunner;
import io.workflowengine.execution.application.port.out.WorkflowCatalog;
import io.workflowengine.execution.domain.model.PublishedVersion;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Maintains the local projections of workflow ACLs and published versions from workflow-service events. */
public class WorkflowProjectionService {

    public static final String CONSUMER = "workflow-events";

    private final WorkflowCatalog catalog;
    private final MessageDeduplicator deduplicator;
    private final TransactionRunner tx;

    public WorkflowProjectionService(WorkflowCatalog catalog, MessageDeduplicator deduplicator, TransactionRunner tx) {
        this.catalog = catalog;
        this.deduplicator = deduplicator;
        this.tx = tx;
    }

    public void accessUpdated(
            UUID eventId,
            UUID workflowId,
            String ownerId,
            long aclRevision,
            boolean archived,
            Map<String, String> roles) {
        once(eventId, () -> catalog.replaceAccess(workflowId, ownerId, aclRevision, archived, roles));
    }

    public void versionPublished(UUID eventId, PublishedVersion version, String ownerId, Instant publishedAt) {
        once(eventId, () -> catalog.saveVersion(version, ownerId, publishedAt));
    }

    public void archived(UUID eventId, UUID workflowId) {
        once(eventId, () -> catalog.markArchived(workflowId));
    }

    private void once(UUID eventId, Runnable action) {
        tx.inTransaction(() -> {
            if (deduplicator.firstDelivery(CONSUMER, eventId)) {
                action.run();
            }
            return null;
        });
    }
}
