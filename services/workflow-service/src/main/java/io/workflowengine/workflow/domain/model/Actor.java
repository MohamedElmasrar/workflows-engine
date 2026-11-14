package io.workflowengine.workflow.domain.model;

import java.util.Objects;

/** The user performing an operation. {@code admin} reflects the platform-wide ADMIN realm role. */
public record Actor(String userId, boolean admin) {

    public Actor {
        Objects.requireNonNull(userId, "userId");
    }
}
