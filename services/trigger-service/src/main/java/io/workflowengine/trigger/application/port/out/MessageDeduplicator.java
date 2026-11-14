package io.workflowengine.trigger.application.port.out;

import java.util.UUID;

public interface MessageDeduplicator {
    boolean firstDelivery(String consumer, UUID eventId);
}
