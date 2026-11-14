package io.workflowengine.workflow.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.Topics;
import io.workflowengine.workflow.TestcontainersConfiguration;
import io.workflowengine.workflow.domain.DefinitionFixtures;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkflowApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaConnectionDetails kafkaConnection;

    static RequestPostProcessor user(String id) {
        return jwt().jwt(j -> j.subject(id).claim("preferred_username", id))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    @Test
    void fullLifecycleCreateEditValidatePublishAndShare() throws Exception {
        JsonNode created = call(withJson(post("/api/v1/workflows").with(user("alice")), Map.of("name", "Orders")), 201);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("role").asText()).isEqualTo("OWNER");

        JsonNode definition = DefinitionFixtures.definition()
                .manualTrigger("start")
                .log("hello", "Hello {{ trigger.name }}")
                .edge("start", "hello")
                .json();
        JsonNode updated = call(
                withJson(
                        put("/api/v1/workflows/" + id).with(user("alice")),
                        Map.of("name", "Orders v2", "definition", definition, "revision", 1)),
                200);
        assertThat(updated.get("revision").asLong()).isEqualTo(2);

        // stale revision → 409
        call(
                withJson(
                        put("/api/v1/workflows/" + id).with(user("alice")),
                        Map.of("name", "Lost update", "definition", definition, "revision", 1)),
                409);

        JsonNode validation = call(post("/api/v1/workflows/" + id + "/validate").with(user("alice")), 200);
        assertThat(validation.get("valid").asBoolean()).isTrue();

        JsonNode version = call(post("/api/v1/workflows/" + id + "/publish").with(user("alice")), 201);
        assertThat(version.get("version").asInt()).isEqualTo(1);
        call(get("/api/v1/workflows/" + id + "/versions").with(user("alice")), 200);

        // bob is unknown to alice's workflow → 404 (existence not revealed)
        call(get("/api/v1/workflows/" + id).with(user("bob")), 404);
        call(get("/api/v1/me").with(user("bob")), 200); // bob signs in once
        call(withJson(put("/api/v1/workflows/" + id + "/permissions/bob").with(user("alice")), Map.of("role", "VIEWER")), 200);
        JsonNode asBob = call(get("/api/v1/workflows/" + id).with(user("bob")), 200);
        assertThat(asBob.get("role").asText()).isEqualTo("VIEWER");
        call(post("/api/v1/workflows/" + id + "/publish").with(user("bob")), 403);

        JsonNode bobList = call(get("/api/v1/workflows?search=orders").with(user("bob")), 200);
        assertThat(bobList.get("totalItems").asInt()).isEqualTo(1);

        List<EventEnvelope> events = consume(id, 3);
        assertThat(events).extracting(EventEnvelope::eventType)
                .containsSubsequence("WorkflowAccessUpdated", "WorkflowVersionPublished", "WorkflowAccessUpdated");
        EventEnvelope published = events.stream()
                .filter(e -> e.eventType().equals("WorkflowVersionPublished")).findFirst().orElseThrow();
        assertThat(published.payload().at("/definition/nodes/1/id").asText()).isEqualTo("hello");
    }

    @Test
    void publishingAnInvalidWorkflowReturns422WithErrors() throws Exception {
        String id = call(withJson(post("/api/v1/workflows").with(user("carol")), Map.of("name", "Broken")), 201)
                .get("id").asText();
        JsonNode definition = DefinitionFixtures.definition()
                .manualTrigger("start")
                .log("orphan", "x")
                .json();
        call(withJson(put("/api/v1/workflows/" + id).with(user("carol")), Map.of("name", "Broken", "definition", definition, "revision", 1)), 200);
        mvc.perform(post("/api/v1/workflows/" + id + "/publish").with(user("carol")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("UNREACHABLE_NODE"))
                .andExpect(jsonPath("$.errors[0].nodeId").value("orphan"));
    }

    @Test
    void schemaViolationsAreRejectedOnSave() throws Exception {
        String id = call(withJson(post("/api/v1/workflows").with(user("dave")), Map.of("name", "Bad")), 201)
                .get("id").asText();
        ObjectNode definition = (ObjectNode) DefinitionFixtures.definition().manualTrigger("start").json();
        definition.put("script", "evil()");
        mvc.perform(withJson(put("/api/v1/workflows/" + id).with(user("dave")), Map.of("name", "Bad", "definition", definition, "revision", 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("SCHEMA_VIOLATION"));
    }

    @Test
    void archivedWorkflowsDisappearFromDefaultList() throws Exception {
        String id = call(withJson(post("/api/v1/workflows").with(user("erin")), Map.of("name", "Temp")), 201)
                .get("id").asText();
        call(delete("/api/v1/workflows/" + id).with(user("erin")), 204);
        assertThat(call(get("/api/v1/workflows").with(user("erin")), 200).get("totalItems").asInt()).isZero();
        assertThat(call(get("/api/v1/workflows?status=ARCHIVED").with(user("erin")), 200).get("totalItems").asInt())
                .isEqualTo(1);
    }

    @Test
    void publishedVersionsAreImmutableInTheDatabase() throws Exception {
        String id = call(withJson(post("/api/v1/workflows").with(user("frank")), Map.of("name", "Immutable")), 201)
                .get("id").asText();
        call(post("/api/v1/workflows/" + id + "/publish").with(user("frank")), 201);
        assertThatThrownBy(() -> jdbc.update("update workflow_version set name = 'hacked' where workflow_id = ?::uuid", id))
                .hasMessageContaining("immutable");
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/workflows")).andExpect(status().isUnauthorized());
    }

    private List<EventEnvelope> consume(String workflowId, int atLeast) throws Exception {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<EventEnvelope> result = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topics.WORKFLOW_EVENTS));
            long deadline = System.currentTimeMillis() + 20_000;
            while (result.size() < atLeast && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(workflowId)) {
                        result.add(json.readValue(record.value(), EventEnvelope.class));
                    }
                }
            }
        }
        return result;
    }
}
