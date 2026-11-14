package io.workflowengine.execution.application.port.out;

import java.util.UUID;

/** Persistent record of consumed event ids; must participate in the caller's transaction. */
public interface MessageDeduplicator {

    boolean firstDelivery(String consumer, UUID eventId);
}
