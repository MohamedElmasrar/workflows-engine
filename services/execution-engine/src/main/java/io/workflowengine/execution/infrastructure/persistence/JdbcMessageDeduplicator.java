package io.workflowengine.execution.infrastructure.persistence;

import io.workflowengine.execution.application.port.out.MessageDeduplicator;
import io.workflowengine.platform.messaging.ProcessedMessageStore;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcMessageDeduplicator implements MessageDeduplicator {

    private final ProcessedMessageStore store;

    @Override
    public boolean firstDelivery(String consumer, UUID eventId) {
        return store.markProcessed(consumer, eventId);
    }
}
