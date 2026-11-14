package io.workflowengine.execution.application.port.out;

import io.workflowengine.execution.domain.model.PublishedVersion;
import io.workflowengine.execution.domain.model.WorkflowAccess;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Projections of workflow-service data (ACLs and published versions) owned by this service. */
public interface WorkflowCatalog {

    Optional<WorkflowAccess> access(UUID workflowId);

    Optional<PublishedVersion> version(UUID workflowId, int version);

    Optional<PublishedVersion> latestVersion(UUID workflowId);

    /** Serializes execution starts per workflow so concurrency limits hold across engine replicas. */
    void lockForScheduling(UUID workflowId);

    /** Replaces the ACL if {@code aclRevision} is newer than the stored one. Returns whether it was applied. */
    boolean replaceAccess(
            UUID workflowId, String ownerId, long aclRevision, boolean archived, Map<String, String> roles);

    void saveVersion(PublishedVersion version, String ownerId, Instant publishedAt);

    void markArchived(UUID workflowId);
}
