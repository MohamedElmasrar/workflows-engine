package io.workflowengine.trigger.application.port.out;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** ACL projection from WorkflowAccessUpdated events. */
public interface AccessRepository {

    boolean replace(UUID workflowId, String ownerId, long aclRevision, Map<String, String> roles);

    Optional<String> roleOf(UUID workflowId, String userId);
}
