package io.workflowengine.worker.domain;

import static io.workflowengine.worker.domain.ConditionHandlerTest.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.handler.HttpRequestHandler;
import io.workflowengine.worker.domain.model.NodeResult;
import io.workflowengine.worker.domain.port.HttpGateway;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpRequestHandlerTest {

    private HttpGateway.HttpCall lastCall;
    private HttpGateway.HttpResponseData next;
    private final HttpRequestHandler handler = new HttpRequestHandler(call -> {
        lastCall = call;
        return next;
    }, new ObjectMapper());

    private NodeResult run(String config) {
        return handler.execute(Contexts.context(NodeType.HTTP_REQUEST, Duration.ofSeconds(3)), json(config));
    }

    private static HttpGateway.HttpResponseData response(int status, String body) {
        return new HttpGateway.HttpResponseData(status,
                Map.of("content-type", List.of("application/json"), "set-cookie", List.of("session=secret")),
                body.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    @Test
    void sendsJsonBodyQueryAndIdempotencyKeyAndParsesJsonResponse() {
        next = response(200, "{\"score\":91}");
        NodeResult result = run("{\"method\":\"POST\",\"url\":\"https://api.example.com/score\",\"query\":{\"q\":\"a b\"},"
                + "\"headers\":{\"X-Api\":\"1\"},\"body\":{\"orderId\":7}}");

        assertThat(lastCall.url()).isEqualTo("https://api.example.com/score?q=a+b");
        assertThat(lastCall.headers()).containsEntry("Content-Type", "application/json")
                .containsEntry("Idempotency-Key", "00000000-0000-0000-0000-000000000001:node");
        assertThat(new String(lastCall.body(), StandardCharsets.UTF_8)).isEqualTo("{\"orderId\":7}");
        assertThat(lastCall.timeout()).isEqualTo(Duration.ofSeconds(3));
        NodeResult.Success success = (NodeResult.Success) result;
        assertThat(success.output().at("/body/score").asInt()).isEqualTo(91);
        assertThat(success.output().at("/headers").has("set-cookie")).isFalse();
    }

    @Test
    void classifiesErrorStatuses() {
        next = response(503, "{}");
        assertThat(run("{\"method\":\"GET\",\"url\":\"https://x.io\"}"))
                .isEqualTo(new NodeResult.Failure("HTTP_5XX", "GET x.io returned HTTP 503", true));
        next = response(429, "{}");
        assertThat(((NodeResult.Failure) run("{\"method\":\"GET\",\"url\":\"https://x.io\"}")).retryable()).isTrue();
        next = response(404, "{}");
        assertThat(((NodeResult.Failure) run("{\"method\":\"GET\",\"url\":\"https://x.io\"}")).retryable()).isFalse();
        next = response(404, "{}");
        assertThat(run("{\"method\":\"GET\",\"url\":\"https://x.io\",\"failOnHttpError\":false}"))
                .isInstanceOf(NodeResult.Success.class);
    }
}
