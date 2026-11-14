package io.workflowengine.worker.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.execution.NodeExecutionCompleted;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.platform.messaging.EventCodec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Import(WorkerFlowIT.Containers.class)
class WorkerFlowIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.2"));
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        }
    }

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    EventCodec codec;

    @Autowired
    ObjectMapper json;

    @Autowired
    KafkaConnectionDetails connection;

    @Test
    void processesTasksAndAnswersRedeliveriesFromTheAttemptCache() throws Exception {
        UUID executionId = UUID.randomUUID();
        NodeExecutionScheduled task = new NodeExecutionScheduled(executionId, UUID.randomUUID(), "shape", "TRANSFORM",
                UUID.randomUUID(), 1, 5000, json.readTree("{\"set\":{\"answer\":42}}"));
        EventEnvelope envelope = codec.envelope(EventTypes.NODE_EXECUTION_SCHEDULED, executionId.toString(), task, "corr-1", null);

        ConcurrentLinkedQueue<EventEnvelope> results = new ConcurrentLinkedQueue<>();
        Thread consumerThread = Thread.ofVirtual().start(() -> {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                    "bootstrap.servers", String.join(",", connection.getBootstrapServers()),
                    "group.id", "it-" + UUID.randomUUID(), "auto.offset.reset", "earliest",
                    "key.deserializer", StringDeserializer.class, "value.deserializer", StringDeserializer.class))) {
                consumer.subscribe(List.of(Topics.NODE_RESULTS));
                while (!Thread.currentThread().isInterrupted()) {
                    consumer.poll(Duration.ofMillis(200)).forEach(r -> results.add(codec.read(r.value())));
                }
            } catch (org.apache.kafka.common.errors.InterruptException ignored) {
                // test finished
            }
        });

        kafka.send(Topics.NODE_TASKS, executionId + ":shape", codec.write(envelope)).join();
        await().atMost(Duration.ofSeconds(60)).until(() -> results.stream()
                .anyMatch(e -> e.eventType().equals(EventTypes.NODE_EXECUTION_COMPLETED)));
        EventEnvelope completed = results.stream()
                .filter(e -> e.eventType().equals(EventTypes.NODE_EXECUTION_COMPLETED)).findFirst().orElseThrow();
        NodeExecutionCompleted payload = codec.payload(completed, NodeExecutionCompleted.class);
        assertThat(payload.output().get("answer").asInt()).isEqualTo(42);
        assertThat(completed.correlationId()).isEqualTo("corr-1");
        assertThat(completed.causationId()).isEqualTo(envelope.eventId().toString());

        // Redelivery of the same task: the cached result is re-published, no second Started is emitted.
        kafka.send(Topics.NODE_TASKS, executionId + ":shape", codec.write(envelope)).join();
        await().atMost(Duration.ofSeconds(30)).until(() -> results.stream()
                .filter(e -> e.eventType().equals(EventTypes.NODE_EXECUTION_COMPLETED)).count() == 2);
        assertThat(results.stream().filter(e -> e.eventType().equals(EventTypes.NODE_EXECUTION_STARTED))).hasSize(1);
        NodeExecutionCompleted replay = results.stream()
                .filter(e -> e.eventType().equals(EventTypes.NODE_EXECUTION_COMPLETED))
                .map(e -> codec.payload(e, NodeExecutionCompleted.class)).toList().get(1);
        assertThat(replay.completedAt()).isEqualTo(payload.completedAt());
        consumerThread.interrupt();
    }
}
