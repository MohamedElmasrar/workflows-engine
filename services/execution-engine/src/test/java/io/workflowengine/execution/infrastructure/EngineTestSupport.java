package io.workflowengine.execution.infrastructure;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.contracts.workflow.WorkflowAccessUpdated;
import io.workflowengine.contracts.workflow.WorkflowVersionPublished;
import io.workflowengine.execution.TestcontainersConfiguration;
import io.workflowengine.platform.messaging.EventCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Shared setup: real PostgreSQL and Kafka, a background consumer that collects dispatched worker tasks, and helpers
 * that play the role of the workflow service and the worker by publishing contract events.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
        properties = {
            "engine.lease-grace=2s",
            "engine.sweeper.interval=200ms",
            "platform.outbox.poll-interval=50ms",
            "platform.messaging.retry-max-elapsed=5s",
            "platform.messaging.retry-initial-interval=200ms"
        })
public abstract class EngineTestSupport {

    @Autowired
    protected KafkaTemplate<String, String> kafka;

    @Autowired
    protected EventCodec codec;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    KafkaConnectionDetails kafkaConnection;

    protected final ConcurrentLinkedQueue<EventEnvelope> tasks = new ConcurrentLinkedQueue<>();
    private Thread collector;
    private volatile boolean running;

    @BeforeEach
    void startTaskCollector() {
        running = true;
        collector = Thread.ofPlatform().start(() -> {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    String.join(",", kafkaConnection.getBootstrapServers()),
                    ConsumerConfig.GROUP_ID_CONFIG,
                    "test-worker-" + UUID.randomUUID(),
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                    "latest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                    StringDeserializer.class))) {
                consumer.subscribe(List.of(Topics.NODE_TASKS));
                while (running) {
                    for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(200))) {
                        tasks.add(codec.read(r.value()));
                    }
                }
            }
        });
        // give the consumer time to join before tests produce tasks
        await().pollDelay(Duration.ofSeconds(3)).until(() -> true);
    }

    @AfterEach
    void stopTaskCollector() throws InterruptedException {
        running = false;
        collector.join(5000);
    }

    protected UUID publishWorkflow(String ownerId, JsonNode definition) {
        UUID workflowId = UUID.randomUUID();
        send(
                Topics.WORKFLOW_EVENTS,
                EventTypes.WORKFLOW_ACCESS_UPDATED,
                workflowId.toString(),
                new WorkflowAccessUpdated(
                        workflowId,
                        ownerId,
                        1,
                        List.of(new WorkflowAccessUpdated.AccessEntry(ownerId, "OWNER")),
                        false));
        send(
                Topics.WORKFLOW_EVENTS,
                EventTypes.WORKFLOW_VERSION_PUBLISHED,
                workflowId.toString(),
                new WorkflowVersionPublished(workflowId, 1, "test", ownerId, ownerId, Instant.now(), definition));
        await().atMost(Duration.ofSeconds(20))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from workflow_version_snapshot where workflow_id = ?",
                                Long.class,
                                workflowId)
                        == 1);
        return workflowId;
    }

    protected EventEnvelope send(String topic, String type, String key, Object payload) {
        EventEnvelope envelope =
                codec.envelope(type, key, payload, UUID.randomUUID().toString(), null);
        sendRaw(topic, key, envelope);
        return envelope;
    }

    protected void sendRaw(String topic, String key, EventEnvelope envelope) {
        kafka.send(topic, key, codec.write(envelope)).join();
    }

    protected NodeExecutionScheduled awaitTask(UUID executionId, String nodeId, int attemptNumber) {
        return await().atMost(Duration.ofSeconds(30))
                .until(
                        () -> tasks.stream()
                                .map(e -> codec.payload(e, NodeExecutionScheduled.class))
                                .filter(t -> t.executionId().equals(executionId)
                                        && t.nodeId().equals(nodeId)
                                        && t.attemptNumber() == attemptNumber)
                                .findFirst()
                                .orElse(null),
                        t -> t != null);
    }

    protected String executionStatus(UUID executionId) {
        return jdbc.queryForObject("select status from workflow_execution where id = ?", String.class, executionId);
    }

    protected String nodeStatus(UUID executionId, String nodeId) {
        return jdbc.queryForObject(
                "select status from node_execution where execution_id = ? and node_id = ?",
                String.class,
                executionId,
                nodeId);
    }

    protected void awaitExecutionStatus(UUID executionId, String status) {
        await().atMost(Duration.ofSeconds(30)).until(() -> status.equals(executionStatus(executionId)));
    }

    protected long historyCount(UUID executionId, String nodeId, String status) {
        return jdbc.queryForObject(
                "select count(*) from execution_event where execution_id = ? and node_id = ? and status = ? and event_type = 'NODE_STATUS_CHANGED'",
                Long.class,
                executionId,
                nodeId,
                status);
    }
}
