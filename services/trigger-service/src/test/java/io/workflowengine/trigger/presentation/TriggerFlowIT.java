package io.workflowengine.trigger.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.EventEnvelope;
import io.workflowengine.contracts.EventTypes;
import io.workflowengine.contracts.Topics;
import io.workflowengine.contracts.workflow.WorkflowAccessUpdated;
import io.workflowengine.contracts.workflow.WorkflowVersionPublished;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.trigger.TestcontainersConfiguration;
import io.workflowengine.trigger.application.usecase.ScheduleService;
import io.workflowengine.trigger.domain.service.WebhookSignature;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
        properties = {
            "trigger.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "trigger.scheduler.enabled=false",
            "platform.outbox.poll-interval=100ms"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TriggerFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    EventCodec codec;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ScheduleService schedules;

    private UUID publish(String triggerNode) throws Exception {
        UUID workflowId = UUID.randomUUID();
        JsonNode definition = json.readTree("{\"schemaVersion\":1,\"nodes\":[" + triggerNode + "],\"edges\":[]}");
        send(
                EventTypes.WORKFLOW_ACCESS_UPDATED,
                workflowId,
                new WorkflowAccessUpdated(
                        workflowId,
                        "alice",
                        1,
                        List.of(new WorkflowAccessUpdated.AccessEntry("alice", "OWNER")),
                        false));
        send(
                EventTypes.WORKFLOW_VERSION_PUBLISHED,
                workflowId,
                new WorkflowVersionPublished(workflowId, 1, "wf", "alice", "alice", Instant.now(), definition));
        await().atMost(Duration.ofSeconds(30))
                .until(() -> jdbc.queryForObject(
                                        "select count(*) from trigger_registration where workflow_id = ?",
                                        Long.class,
                                        workflowId)
                                == 1
                        && jdbc.queryForObject(
                                        "select count(*) from workflow_access where workflow_id = ?",
                                        Long.class,
                                        workflowId)
                                == 1);
        return workflowId;
    }

    private void send(String type, UUID key, Object payload) {
        EventEnvelope e = codec.envelope(type, key.toString(), payload, "c", null);
        kafka.send(Topics.WORKFLOW_EVENTS, key.toString(), codec.write(e)).join();
    }

    private MockHttpServletRequestBuilder webhook(String path, String body, long ts, String signature) {
        MockHttpServletRequestBuilder b =
                post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            b.header("X-Webhook-Timestamp", Long.toString(ts)).header("X-Webhook-Signature", signature);
        }
        return b;
    }

    @Test
    void signedWebhookStartsExactlyOneExecutionAndRejectsInvalidRequests() throws Exception {
        UUID workflowId = publish("{\"id\":\"hook\",\"type\":\"WEBHOOK_TRIGGER\",\"name\":\"h\",\"config\":"
                + "{\"requireSignature\":true,\"requiredFields\":[\"orderId\"]}}");
        var alice = jwt().jwt(j -> j.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
        JsonNode triggers = json.readTree(
                mvc.perform(get("/api/v1/workflows/" + workflowId + "/triggers").with(alice))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        String url = triggers.at("/webhook/url").asText();
        String path = url.substring(url.indexOf("/api/v1/webhooks/"));
        String secret = json.readTree(mvc.perform(post("/api/v1/workflows/" + workflowId + "/triggers/webhook/secret")
                                .with(alice))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("secret")
                .asText();

        // another user cannot see the webhook
        mvc.perform(get("/api/v1/workflows/" + workflowId + "/triggers")
                        .with(jwt().jwt(j -> j.subject("mallory"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound());

        String body = "{\"orderId\":\"o-1\",\"amount\":120}";
        long now = Instant.now().getEpochSecond();
        String sig = WebhookSignature.sign(secret, now, body.getBytes(StandardCharsets.UTF_8));

        mvc.perform(webhook(path, body, now, sig)).andExpect(status().isAccepted());
        mvc.perform(webhook(path, body, now, sig)).andExpect(status().isOk()); // replay → duplicate
        mvc.perform(webhook(path, body, now, "sha256=deadbeef")).andExpect(status().isUnauthorized());
        mvc.perform(webhook(path, body, now - 3600, WebhookSignature.sign(secret, now - 3600, body.getBytes())))
                .andExpect(status().isUnauthorized());
        String noOrder = "{\"amount\":1}";
        mvc.perform(webhook(path, noOrder, now, WebhookSignature.sign(secret, now, noOrder.getBytes())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(webhook("/api/v1/webhooks/not-a-real-token", body, now, sig))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject(
                        "select count(*) from trigger_delivery where workflow_id = ?", Long.class, workflowId))
                .isEqualTo(1);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from outbox_event where event_type = 'WorkflowExecutionRequested' and message_key = ? and published_at is not null",
                                Long.class,
                                workflowId.toString())
                        == 1);
        String envelope = jdbc.queryForObject(
                "select envelope::text from outbox_event where event_type = 'WorkflowExecutionRequested' and message_key = ?",
                String.class,
                workflowId.toString());
        assertThat(json.readTree(envelope).at("/payload/input/body/orderId").asText())
                .isEqualTo("o-1");
        assertThat(envelope).doesNotContain(secret);
    }

    @Test
    void concurrentSchedulerRunsFireEachDueTimeOnce() throws Exception {
        UUID workflowId = publish("{\"id\":\"cron\",\"type\":\"SCHEDULE_TRIGGER\",\"name\":\"c\",\"config\":"
                + "{\"cron\":\"*/5 * * * *\",\"timezone\":\"Europe/Paris\"}}");
        jdbc.update(
                "update scheduled_trigger set next_fire_at = now() - interval '1 minute' where workflow_id = ?",
                workflowId);

        List<CompletableFuture<Integer>> runs = List.of(
                CompletableFuture.supplyAsync(() -> schedules.fireDue(10)),
                CompletableFuture.supplyAsync(() -> schedules.fireDue(10)),
                CompletableFuture.supplyAsync(() -> schedules.fireDue(10)));
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();

        assertThat(jdbc.queryForObject(
                        "select count(*) from trigger_delivery where workflow_id = ?", Long.class, workflowId))
                .isEqualTo(1);
        Instant next = jdbc.queryForObject(
                        "select next_fire_at from scheduled_trigger where workflow_id = ?",
                        java.sql.Timestamp.class,
                        workflowId)
                .toInstant();
        assertThat(next).isAfter(Instant.now());
    }
}
