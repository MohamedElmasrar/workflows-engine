package io.workflowengine.platform.messaging;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistent deduplication for idempotent consumers. Must be called inside the transaction that applies the state
 * change, so that "processed" and "applied" are committed atomically.
 */
@RequiredArgsConstructor
public class ProcessedMessageStore {

    private final JdbcTemplate jdbcTemplate;

    /** Returns {@code true} the first time a consumer sees an event id, {@code false} for duplicates. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumer, UUID eventId) {
        int inserted = jdbcTemplate.update(
                "insert into processed_message (consumer, event_id) values (?, ?) on conflict do nothing",
                consumer,
                eventId);
        return inserted == 1;
    }
}
