package io.workflowengine.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.workflowengine.contracts.execution.NodeError;
import io.workflowengine.contracts.execution.NodeExecutionCompleted;
import io.workflowengine.contracts.execution.NodeExecutionFailed;
import io.workflowengine.contracts.execution.NodeExecutionScheduled;
import io.workflowengine.contracts.execution.NodeExecutionStarted;
import io.workflowengine.contracts.execution.WorkflowExecutionLifecycleEvent;
import io.workflowengine.contracts.execution.WorkflowExecutionRequested;
import io.workflowengine.contracts.workflow.WorkflowAccessUpdated;
import io.workflowengine.contracts.workflow.WorkflowArchived;
import io.workflowengine.contracts.workflow.WorkflowVersionPublished;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies that the Java contract records serialize to JSON that satisfies the published JSON schemas. */
class ContractSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG =
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    private static final UUID ID = UUID.fromString("7f8b3a52-3c6f-4a5e-9b1d-0d6c1f6b9a10");
    private static final Instant NOW = Instant.parse("2025-01-01T10:00:00Z");

    static Stream<Arguments> events() throws IOException {
        JsonNode definition = readExample();
        ObjectNode config = MAPPER.createObjectNode().put("message", "hello");
        return Stream.of(
                Arguments.of(
                        "WorkflowAccessUpdated",
                        new WorkflowAccessUpdated(
                                ID, "owner", 1, List.of(new WorkflowAccessUpdated.AccessEntry("u2", "EDITOR")), false)),
                Arguments.of(
                        "WorkflowVersionPublished",
                        new WorkflowVersionPublished(ID, 1, "demo", "owner", "owner", NOW, definition)),
                Arguments.of("WorkflowArchived", new WorkflowArchived(ID, NOW)),
                Arguments.of(
                        "WorkflowExecutionRequested",
                        new WorkflowExecutionRequested(
                                ID, ID, 1, "WEBHOOK", "webhook:1", MAPPER.createObjectNode(), "delivery-1")),
                Arguments.of(
                        "NodeExecutionScheduled",
                        new NodeExecutionScheduled(ID, ID, "log", "LOG", ID, 1, 30000, config)),
                Arguments.of("NodeExecutionStarted", new NodeExecutionStarted(ID, "log", ID, "worker-1", NOW)),
                Arguments.of(
                        "NodeExecutionCompleted",
                        new NodeExecutionCompleted(ID, "check", ID, "worker-1", config, "true", 5, NOW)),
                Arguments.of(
                        "NodeExecutionFailed",
                        new NodeExecutionFailed(
                                ID, "http", ID, "worker-1", new NodeError("HTTP_5XX", "503", true), 5, NOW)),
                Arguments.of(
                        "WorkflowExecutionLifecycle",
                        new WorkflowExecutionLifecycleEvent(
                                ID,
                                ID,
                                1,
                                "FAILED",
                                NOW,
                                new WorkflowExecutionLifecycleEvent.ExecutionError("NODE_FAILED", "boom"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    void payloadMatchesSchema(String schemaName, Object payload) throws IOException {
        JsonNode json = MAPPER.valueToTree(payload);
        assertThat(validate("events/" + schemaName + ".v1.json", json)).isEmpty();

        EventEnvelope envelope =
                new EventEnvelope(UUID.randomUUID(), schemaName, 1, ID.toString(), "corr", null, NOW, json);
        assertThat(validate("events/EventEnvelope.v1.json", MAPPER.valueToTree(envelope)))
                .isEmpty();
    }

    @Test
    void envelopeRoundTripsThroughJson() throws IOException {
        EventEnvelope envelope = new EventEnvelope(
                ID,
                "WorkflowArchived",
                1,
                ID.toString(),
                "c",
                "d",
                NOW,
                MAPPER.valueToTree(new WorkflowArchived(ID, NOW)));
        EventEnvelope copy = MAPPER.readValue(MAPPER.writeValueAsBytes(envelope), EventEnvelope.class);
        assertThat(copy).isEqualTo(envelope);
        assertThat(MAPPER.treeToValue(copy.payload(), WorkflowArchived.class).workflowId())
                .isEqualTo(ID);
    }

    @Test
    void demoDefinitionIsValid() throws IOException {
        assertThat(validate("workflow-definition.v1.json", readExample())).isEmpty();
    }

    @Test
    void definitionRejectsUnknownNodeTypeAndBadConfig() throws IOException {
        ObjectNode definition = (ObjectNode) readExample();
        ((ObjectNode) definition.withArray("nodes").get(0)).put("type", "SHELL");
        ((ObjectNode) definition.withArray("nodes").get(2).get("config")).put("method", "TRACE");
        Set<ValidationMessage> errors = validate("workflow-definition.v1.json", definition);
        assertThat(errors)
                .extracting(ValidationMessage::getInstanceLocation)
                .map(Object::toString)
                .contains("/nodes/0/type", "/nodes/2/config/method");
    }

    @Test
    void definitionRejectsUnknownTopLevelProperty() throws IOException {
        ObjectNode definition = (ObjectNode) readExample();
        definition.put("script", "rm -rf /");
        assertThat(validate("workflow-definition.v1.json", definition)).isNotEmpty();
    }

    private static Set<ValidationMessage> validate(String schemaPath, JsonNode json) throws IOException {
        try (InputStream in = ContractSchemaTest.class.getResourceAsStream("/schemas/" + schemaPath)) {
            JsonSchema schema = FACTORY.getSchema(in, CONFIG);
            return schema.validate(json);
        }
    }

    private static JsonNode readExample() throws IOException {
        try (InputStream in = ContractSchemaTest.class.getResourceAsStream("/examples/order-webhook-demo.json")) {
            return MAPPER.readTree(in);
        }
    }
}
