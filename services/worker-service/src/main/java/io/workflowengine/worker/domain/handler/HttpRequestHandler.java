package io.workflowengine.worker.domain.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;
import io.workflowengine.worker.domain.port.HttpGateway;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * HTTP Request node. Builds the request from resolved configuration, classifies the response (5xx/429 retryable, other
 * 4xx not) and returns status, selected headers and the body (parsed as JSON when possible).
 */
@RequiredArgsConstructor
public class HttpRequestHandler implements NodeHandler {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> FORBIDDEN_HEADERS =
            Set.of("host", "content-length", "connection", "transfer-encoding");
    private static final Set<String> HIDDEN_RESPONSE_HEADERS =
            Set.of("set-cookie", "authorization", "proxy-authenticate");

    private final HttpGateway gateway;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType supportedType() {
        return NodeType.HTTP_REQUEST;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context, JsonNode config) {
        String method = config.path("method").asText("GET");
        if (!METHODS.contains(method)) {
            throw NodeExecutionException.invalidConfig("Unsupported HTTP method " + method);
        }
        String url = withQuery(config.path("url").asText(""), config.get("query"));
        if (!url.matches("(?i)^https?://.+")) {
            throw NodeExecutionException.invalidConfig("URL must be http(s): " + url);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode configured = config.get("headers");
        if (configured != null && configured.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = configured.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> h = it.next();
                if (FORBIDDEN_HEADERS.contains(h.getKey().toLowerCase())) {
                    throw NodeExecutionException.invalidConfig("Header " + h.getKey() + " cannot be set");
                }
                headers.put(h.getKey(), h.getValue().asText());
            }
        }
        headers.keySet().stream()
                .filter(k -> k.equalsIgnoreCase("Idempotency-Key"))
                .findAny()
                .ifPresentOrElse(k -> {}, () -> headers.put("Idempotency-Key", context.idempotencyKey()));
        byte[] body = null;
        JsonNode bodyNode = config.get("body");
        if (bodyNode != null && !bodyNode.isNull() && !method.equals("GET")) {
            body = serialize(bodyNode);
            headers.keySet().stream()
                    .filter(k -> k.equalsIgnoreCase("Content-Type"))
                    .findAny()
                    .ifPresentOrElse(
                            k -> {},
                            () -> headers.put(
                                    "Content-Type",
                                    bodyNode.isTextual() ? "text/plain; charset=utf-8" : "application/json"));
        }

        HttpGateway.HttpResponseData response =
                gateway.send(new HttpGateway.HttpCall(method, url, headers, body, context.timeout()));

        ObjectNode output = JsonNodeFactory.instance.objectNode().put("status", response.status());
        ObjectNode responseHeaders = output.putObject("headers");
        response.headers().forEach((name, values) -> {
            if (!HIDDEN_RESPONSE_HEADERS.contains(name.toLowerCase()) && !values.isEmpty()) {
                responseHeaders.put(name.toLowerCase(), String.join(", ", values));
            }
        });
        output.set("body", parseBody(response));

        boolean failOnHttpError = config.path("failOnHttpError").asBoolean(true);
        int status = response.status();
        if (failOnHttpError && status >= 400) {
            String code = status >= 500 ? "HTTP_5XX" : status == 429 ? "HTTP_429" : "HTTP_4XX";
            boolean retryable = status >= 500 || status == 429 || status == 408;
            return new NodeResult.Failure(code, method + " " + hostOf(url) + " returned HTTP " + status, retryable);
        }
        return NodeResult.success(output);
    }

    private JsonNode parseBody(HttpGateway.HttpResponseData response) {
        if (response.body() == null || response.body().length == 0) {
            return JsonNodeFactory.instance.nullNode();
        }
        String contentType =
                response.contentType() == null ? "" : response.contentType().toLowerCase();
        String text = new String(response.body(), StandardCharsets.UTF_8);
        if (contentType.contains("json")) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception e) {
                return JsonNodeFactory.instance.textNode(text);
            }
        }
        return JsonNodeFactory.instance.textNode(text);
    }

    private byte[] serialize(JsonNode body) {
        if (body.isTextual()) {
            return body.asText().getBytes(StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw NodeExecutionException.invalidConfig("Body is not serializable");
        }
    }

    static String withQuery(String url, JsonNode query) {
        if (query == null || !query.isObject() || query.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url).append(url.contains("?") ? '&' : '?');
        List<String> parts = new java.util.ArrayList<>();
        query.fields()
                .forEachRemaining(e -> parts.add(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue().asText(), StandardCharsets.UTF_8)));
        return sb.append(String.join("&", parts)).toString();
    }

    private static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }
}
