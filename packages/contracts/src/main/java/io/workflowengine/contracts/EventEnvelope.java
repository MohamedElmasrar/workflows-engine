package io.workflowengine.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Envelope wrapping every event exchanged through Kafka.
 *
 * @param eventId unique id of this event, used for consumer deduplication
 * @param eventType logical type, see {@link EventTypes}
 * @param eventVersion payload schema version
 * @param aggregateId id of the aggregate that emitted the event; also the Kafka record key
 * @param correlationId id shared by all events caused by the same original request
 * @param causationId id of the event or request that directly caused this event, if any
 * @param occurredAt time the state change happened
 * @param payload event-type specific payload
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateId,
        String correlationId,
        String causationId,
        Instant occurredAt,
        JsonNode payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
    }
}
