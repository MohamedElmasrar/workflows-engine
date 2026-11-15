package io.workflowengine.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.MessageContext;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends events to the transactional outbox. Must run inside the transaction that performs the state change, so the
 * event exists if and only if the change was committed.
 */
@RequiredArgsConstructor
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final EventCodec codec;
    private final ObjectMapper objectMapper;
    private final TraceContextCapture traceContext;

    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope append(String topic, String eventType, String aggregateId, Object payload) {
        return append(topic, eventType, aggregateId, aggregateId, payload);
    }

    /** Variant with an explicit Kafka record key, for topics partitioned by something other than the aggregate. */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope append(String topic, String eventType, String aggregateId, String messageKey, Object payload) {
        EventEnvelope envelope = codec.envelope(
                eventType, aggregateId, payload, MessageContext.correlationId(), MessageContext.causationId());
        Map<String, String> headers = traceContext.capture();
        jdbcTemplate.update(
                """
                insert into outbox_event (event_id, topic, message_key, event_type, envelope, headers)
                values (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """,
                envelope.eventId(),
                topic,
                messageKey,
                eventType,
                codec.write(envelope),
                toJson(headers));
        return envelope;
    }

    private String toJson(Map<String, String> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
