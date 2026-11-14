package io.workflowengine.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.MessageContext;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends events to the transactional outbox. Must run inside the transaction that performs the state change, so the
 * event exists if and only if the change was committed.
 */
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final EventCodec codec;
    private final ObjectMapper objectMapper;
    private final TraceContextCapture traceContext;

    public OutboxWriter(
            JdbcTemplate jdbcTemplate, EventCodec codec, ObjectMapper objectMapper, TraceContextCapture traceContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.traceContext = traceContext;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope append(String topic, String eventType, String aggregateId, Object payload) {
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
                aggregateId,
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
