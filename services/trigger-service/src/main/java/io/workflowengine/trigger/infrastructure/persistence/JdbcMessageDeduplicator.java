package io.workflowengine.trigger.infrastructure.persistence;

import io.workflowengine.platform.messaging.ProcessedMessageStore;
import io.workflowengine.trigger.application.port.out.MessageDeduplicator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JdbcMessageDeduplicator implements MessageDeduplicator {

    private final ProcessedMessageStore store;

    public JdbcMessageDeduplicator(ProcessedMessageStore store) {
        this.store = store;
    }

    @Override
    public boolean firstDelivery(String consumer, UUID eventId) {
        return store.markProcessed(consumer, eventId);
    }
}
