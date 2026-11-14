package io.workflowengine.workflow.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Users who have signed in at least once, so workflows can be shared by username. */
public interface UserDirectory {

    void remember(String userId, String username, String email);

    Optional<String> findIdByUsername(String username);

    Map<String, String> usernames(Collection<String> userIds);
}
