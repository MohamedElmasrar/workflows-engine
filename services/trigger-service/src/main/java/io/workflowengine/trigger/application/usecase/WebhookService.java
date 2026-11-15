package io.workflowengine.trigger.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.execution.WorkflowExecutionRequested;
import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import io.workflowengine.trigger.application.port.out.ExecutionRequestPublisher;
import io.workflowengine.trigger.application.port.out.SecretProtector;
import io.workflowengine.trigger.application.port.out.TriggerRepository;
import io.workflowengine.trigger.domain.exception.WebhookRejectedException;
import io.workflowengine.trigger.domain.exception.WebhookRejectedException.Reason;
import io.workflowengine.trigger.domain.model.WebhookEndpoint;
import io.workflowengine.trigger.domain.service.WebhookSignature;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receives webhooks: token lookup, optional HMAC verification with a replay window, JSON payload validation, delivery
 * deduplication, then an execution request through the outbox, all in one transaction.
 */
@RequiredArgsConstructor
public class WebhookService {

    public static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    private static final Set<String> FORWARDED_HEADERS =
            Set.of("content-type", "user-agent", "x-request-id", "x-github-event");

    private final TriggerRepository triggers;
    private final DeliveryRepository deliveries;
    private final SecretProtector secrets;
    private final ExecutionRequestPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public record Incoming(
            String token, String contentType, Map<String, String> headers, Map<String, String> query, byte[] body) {}

    public record Accepted(UUID deliveryId, UUID executionId, boolean duplicate) {}

    @Transactional
    public Accepted receive(Incoming request) {
        if (request.body().length > MAX_PAYLOAD_BYTES) {
            throw new WebhookRejectedException(
                    Reason.PAYLOAD_TOO_LARGE, "Payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
        WebhookEndpoint endpoint = triggers.webhookByTokenHash(secrets.hashToken(request.token()))
                .filter(WebhookEndpoint::active)
                .orElseThrow(() -> new WebhookRejectedException(Reason.NOT_FOUND, "Unknown webhook"));
        Instant now = clock.instant();
        String signature = header(request, "x-webhook-signature");
        if (endpoint.requireSignature()) {
            WebhookSignature.Verdict verdict = WebhookSignature.verify(
                    secrets.decrypt(endpoint.encryptedSecret()),
                    header(request, "x-webhook-timestamp"),
                    signature,
                    request.body(),
                    now);
            if (verdict != WebhookSignature.Verdict.VALID) {
                throw new WebhookRejectedException(Reason.INVALID_SIGNATURE, "Signature check failed: " + verdict);
            }
        }
        if (request.contentType() == null
                || !request.contentType().toLowerCase().contains("json")) {
            throw new WebhookRejectedException(Reason.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json");
        }
        JsonNode body;
        try {
            body = request.body().length == 0 ? objectMapper.createObjectNode() : objectMapper.readTree(request.body());
        } catch (Exception e) {
            throw new WebhookRejectedException(Reason.INVALID_PAYLOAD, "Body is not valid JSON");
        }
        for (String field : endpoint.requiredFields()) {
            JsonNode value = body.at("/" + field.replace(".", "/"));
            if (value.isMissingNode() || value.isNull()) {
                throw new WebhookRejectedException(Reason.INVALID_PAYLOAD, "Missing required field '" + field + "'");
            }
        }

        String dedupeKey = dedupeKey(request, endpoint, signature);
        UUID executionId = UUID.randomUUID();
        DeliveryRepository.InsertResult inserted = deliveries.insertIfAbsent(new DeliveryRepository.Delivery(
                UUID.randomUUID(),
                endpoint.workflowId(),
                "WEBHOOK",
                dedupeKey,
                executionId,
                "REQUESTED",
                request.body().length,
                now));
        if (!inserted.created()) {
            return new Accepted(inserted.delivery().id(), inserted.delivery().executionId(), true);
        }
        ObjectNode input = objectMapper.createObjectNode();
        input.set("body", body);
        ObjectNode headers = input.putObject("headers");
        request.headers().forEach((k, v) -> {
            if (FORWARDED_HEADERS.contains(k.toLowerCase())) {
                headers.put(k.toLowerCase(), v);
            }
        });
        ObjectNode query = input.putObject("query");
        request.query().forEach(query::put);
        input.put("receivedAt", now.toString());
        publisher.publish(new WorkflowExecutionRequested(
                executionId,
                endpoint.workflowId(),
                endpoint.workflowVersion(),
                "WEBHOOK",
                "webhook:" + endpoint.id(),
                input,
                dedupeKey));
        return new Accepted(inserted.delivery().id(), executionId, false);
    }

    private static String dedupeKey(Incoming request, WebhookEndpoint endpoint, String signature) {
        String explicit = header(request, "x-webhook-delivery-id");
        if (explicit == null) {
            explicit = header(request, "idempotency-key");
        }
        if (explicit != null && !explicit.isBlank() && explicit.length() <= 200) {
            return "webhook:" + endpoint.id() + ":" + explicit.strip();
        }
        if (endpoint.requireSignature() && signature != null) {
            return "webhook:" + endpoint.id() + ":sig:" + signature; // a replayed signed request is a duplicate
        }
        return "webhook:" + endpoint.id() + ":" + UUID.randomUUID();
    }

    private static String header(Incoming request, String name) {
        return request.headers().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
