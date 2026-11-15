package io.workflowengine.platform.messaging;

import io.workflowengine.contracts.Topics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Technical delivery retries: exponential back-off on the consumer, then the record goes to {@code <topic>.dlt}
 * with the exception in its headers. Non-retryable failures skip the back-off.
 */
@Slf4j
public final class KafkaErrorHandling {

    private KafkaErrorHandling() {}

    public static DefaultErrorHandler errorHandler(KafkaOperations<?, ?> template, MessagingProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> {
            log.error(
                    "Sending record to dead-letter topic: topic={} partition={} offset={} cause={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex.getMessage());
            return new TopicPartition(Topics.deadLetter(record.topic()), -1);
        });
        ExponentialBackOff backOff = new ExponentialBackOff(
                properties.getRetryInitialInterval().toMillis(), properties.getRetryMultiplier());
        backOff.setMaxInterval(properties.getRetryMaxInterval().toMillis());
        backOff.setMaxElapsedTime(properties.getRetryMaxElapsed().toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableMessageException.class, ConversionException.class);
        return handler;
    }
}
