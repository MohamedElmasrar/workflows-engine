package io.workflowengine.platform.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.EventEnvelope;
import java.time.Instant;
import java.util.UUID;

/** Serializes and deserializes {@link EventEnvelope}s and their payloads. */
public class EventCodec {

    private final ObjectMapper objectMapper;

    public EventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope envelope(
            String eventType, String aggregateId, Object payload, String correlationId, String causationId) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                aggregateId,
                correlationId,
                causationId,
                Instant.now(),
                objectMapper.valueToTree(payload));
    }

    public String write(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + envelope.eventType(), e);
        }
    }

    /** Parses a record value. Malformed messages are not retryable and go to the dead-letter topic. */
    public EventEnvelope read(String json) {
        try {
            return objectMapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new NonRetryableMessageException("Malformed event envelope", e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return objectMapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new NonRetryableMessageException(
                    "Malformed payload for " + envelope.eventType() + " " + envelope.eventId(), e);
        }
    }
}
