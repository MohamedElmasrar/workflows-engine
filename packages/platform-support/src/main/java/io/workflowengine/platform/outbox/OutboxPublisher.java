package io.workflowengine.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polling outbox publisher. A PostgreSQL transaction-scoped advisory lock elects one active publisher per database,
 * which keeps publication in insertion order. A row is marked published only after the broker acknowledged it; if
 * the instance dies between ack and commit the row is sent again, so consumers must be idempotent.
 */
@Slf4j
public class OutboxPublisher {
    private static final long ADVISORY_LOCK_KEY = 0x6f7574626f78L; // "outbox"
    private static final TypeReference<Map<String, String>> HEADERS = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final ObjectMapper objectMapper;
    private final TraceContextCapture traceContext;
    private final Counter published;
    private final Counter failures;

    public OutboxPublisher(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties properties,
            ObjectMapper objectMapper,
            TraceContextCapture traceContext,
            MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.traceContext = traceContext;
        this.published = Counter.builder("outbox.events.published").register(meterRegistry);
        this.failures = Counter.builder("outbox.publish.failures").register(meterRegistry);
        Gauge.builder("outbox.events.pending", this, OutboxPublisher::pendingCount)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${platform.outbox.poll-interval:200ms}")
    public void poll() {
        int sent;
        do {
            sent = publishBatch();
        } while (sent == properties.getBatchSize());
    }

    /** Publishes up to one batch. Returns the number of rows published (0 if another instance holds the lock). */
    public int publishBatch() {
        Integer result = transactionTemplate.execute(status -> {
            Boolean locked = jdbcTemplate.queryForObject(
                    "select pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
            if (!Boolean.TRUE.equals(locked)) {
                return 0;
            }
            List<Row> rows = jdbcTemplate.query(
                    """
                    select id, topic, message_key, event_type, envelope::text, headers::text
                    from outbox_event where published_at is null order by id limit ?
                    """,
                    (rs, i) -> new Row(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6)),
                    properties.getBatchSize());
            int count = 0;
            for (Row row : rows) {
                try {
                    send(row);
                } catch (RuntimeException e) {
                    failures.increment();
                    log.warn("Outbox publication failed for id={} topic={}: {}", row.id(), row.topic(), e.getMessage());
                    jdbcTemplate.update(
                            "update outbox_event set attempts = attempts + 1, last_error = ? where id = ?",
                            truncate(e.getMessage()),
                            row.id());
                    break; // keep ordering: retry this row first on the next poll
                }
                jdbcTemplate.update(
                        "update outbox_event set published_at = now(), attempts = attempts + 1 where id = ?", row.id());
                count++;
            }
            published.increment(count);
            return count;
        });
        return result == null ? 0 : result;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.cleanup-interval:1h}", initialDelay = 60_000)
    public void cleanup() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(properties.getRetention()));
        int outbox = jdbcTemplate.update(
                "delete from outbox_event where published_at is not null and published_at < ?", cutoff);
        int processed = jdbcTemplate.update("delete from processed_message where processed_at < ?", cutoff);
        if (outbox + processed > 0) {
            log.info("Outbox cleanup removed {} published events and {} processed-message records", outbox, processed);
        }
    }

    private void send(Row row) {
        Map<String, String> headers = parseHeaders(row.headers());
        traceContext.runInContext(headers, "outbox publish " + row.eventType(), () -> {
            ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.messageKey(), row.envelope());
            record.headers().add("x-event-type", row.eventType().getBytes(StandardCharsets.UTF_8));
            try {
                kafkaTemplate.send(record).get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while publishing outbox event", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Broker did not acknowledge outbox event: " + e.getMessage(), e);
            }
            return null;
        });
    }

    private Map<String, String> parseHeaders(String json) {
        try {
            return objectMapper.readValue(json, HEADERS);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private double pendingCount() {
        try {
            Long pending = jdbcTemplate.queryForObject(
                    "select count(*) from outbox_event where published_at is null", Long.class);
            return pending == null ? 0 : pending;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record Row(long id, String topic, String messageKey, String eventType, String envelope, String headers) {}
}
