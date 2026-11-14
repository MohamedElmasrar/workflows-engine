package io.workflowengine.execution.domain.model;

import java.util.Map;
import java.util.UUID;

/** Local projection of a workflow's ACL, maintained from WorkflowAccessUpdated events. */
public record WorkflowAccess(UUID workflowId, String ownerId, boolean archived, Map<String, String> roles) {

    public String roleOf(String userId) {
        return roles.get(userId);
    }
}
