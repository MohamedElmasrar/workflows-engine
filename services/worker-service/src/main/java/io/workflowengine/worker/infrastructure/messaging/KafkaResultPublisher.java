package io.workflowengine.worker.infrastructure.messaging;

import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.Topics;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.MessageContext;
import io.workflowengine.worker.application.port.out.ResultPublisher;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publishes results and waits for the broker acknowledgement. The worker has no database, so instead of an outbox it
 * relies on ordering: results are acknowledged before the task offset is committed.
 */
public class KafkaResultPublisher implements ResultPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final EventCodec codec;

    public KafkaResultPublisher(KafkaTemplate<String, String> kafka, EventCodec codec) {
        this.kafka = kafka;
        this.codec = codec;
    }

    @Override
    public void publish(String eventType, String executionId, Object payload) {
        EventEnvelope envelope =
                codec.envelope(eventType, executionId, payload, MessageContext.correlationId(), MessageContext.causationId());
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.NODE_RESULTS, executionId, codec.write(envelope));
        record.headers().add("x-event-type", eventType.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + eventType, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Broker did not acknowledge " + eventType, e);
        }
    }
}
