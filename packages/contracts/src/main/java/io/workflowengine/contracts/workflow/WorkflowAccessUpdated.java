package io.workflowengine.contracts.workflow;

import java.util.List;
import java.util.UUID;

/** Full ACL snapshot of a workflow. Consumers replace their projection and drop older {@code aclRevision}s. */
public record WorkflowAccessUpdated(
        UUID workflowId, String ownerId, long aclRevision, List<AccessEntry> entries, boolean archived) {

    public record AccessEntry(String userId, String role) {}
}
