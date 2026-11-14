package io.workflowengine.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Routes requests to stub upstreams and checks auth, CORS and correlation-id behaviour at the edge. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingTest {

    static final Map<String, String> hits = new ConcurrentHashMap<>();
    static final HttpServer workflow = stub("workflow");
    static final HttpServer engine = stub("engine");
    static final HttpServer trigger = stub("trigger");

    static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                hits.put(
                        name,
                        ex.getRequestURI().getPath() + "|"
                                + ex.getRequestHeaders().getFirst("X-Correlation-Id"));
                byte[] body = ("{\"service\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() {
        workflow.stop(0);
        engine.stop(0);
        trigger.stop(0);
    }

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add(
                "WORKFLOW_SERVICE_URL",
                () -> "http://127.0.0.1:" + workflow.getAddress().getPort());
        registry.add(
                "EXECUTION_ENGINE_URL",
                () -> "http://127.0.0.1:" + engine.getAddress().getPort());
        registry.add(
                "TRIGGER_SERVICE_URL",
                () -> "http://127.0.0.1:" + trigger.getAddress().getPort());
    }

    @TestConfiguration
    static class Stubs {
        /** Accepts the literal token "valid" so the test does not need Keycloak. */
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> token.equals("valid")
                    ? Mono.just(Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject("alice")
                            .build())
                    : Mono.error(new org.springframework.security.oauth2.jwt.BadJwtException("invalid"));
        }

        /** Rate limiting is exercised against Redis in the compose environment; here it always allows. */
        @Bean
        @Primary
        RateLimiter<Object> allowAll() {
            return new RateLimiter<>() {
                public Mono<Response> isAllowed(String routeId, String id) {
                    return Mono.just(new Response(true, Map.of()));
                }

                public Map<String, Object> getConfig() {
                    return Map.of();
                }

                public Class<Object> getConfigClass() {
                    return Object.class;
                }

                public Object newConfig() {
                    return new Object();
                }
            };
        }
    }

    @Autowired
    WebTestClient client;

    private WebTestClient.ResponseSpec get(String path) {
        return client.get().uri(path).header("Authorization", "Bearer valid").exchange();
    }

    @Test
    void routesEachPathToItsOwningService() {
        get("/api/v1/workflows")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.service")
                .isEqualTo("workflow");
        get("/api/v1/workflows/abc/executions")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.service")
                .isEqualTo("engine");
        get("/api/v1/workflows/abc/triggers")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.service")
                .isEqualTo("trigger");
        get("/api/v1/executions/123/events/stream")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.service")
                .isEqualTo("engine");
        get("/api/v1/me")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.service")
                .isEqualTo("workflow");
    }

    @Test
    void rejectsMissingOrInvalidTokensButLetsWebhooksThrough() {
        client.get().uri("/api/v1/workflows").exchange().expectStatus().isUnauthorized();
        client.get()
                .uri("/api/v1/workflows")
                .header("Authorization", "Bearer forged")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post()
                .uri("/api/v1/webhooks/some-token")
                .bodyValue("{}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(hits.get("trigger")).startsWith("/api/v1/webhooks/some-token");
    }

    @Test
    void propagatesCorrelationIds() {
        get("/api/v1/workflows").expectHeader().exists("X-Correlation-Id");
        client.get()
                .uri("/api/v1/workflows")
                .header("Authorization", "Bearer valid")
                .header("X-Correlation-Id", "abc-123")
                .exchange()
                .expectHeader()
                .valueEquals("X-Correlation-Id", "abc-123");
        assertThat(hits.get("workflow")).endsWith("|abc-123");
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    /** Uses a real HTTP client: WebTestClient does not produce a request URI the CORS processor accepts. */
    @Test
    void answersCorsPreflightOnlyForTheConfiguredOrigin() throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        java.util.function.Function<String, java.net.http.HttpResponse<String>> preflight = origin -> {
            try {
                return http.send(
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create("http://localhost:" + port + "/api/v1/workflows"))
                                .method("OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody())
                                .header("Origin", origin)
                                .header("Access-Control-Request-Method", "PUT")
                                .header("Access-Control-Request-Headers", "authorization,content-type")
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        java.net.http.HttpResponse<String> allowed = preflight.apply("http://localhost:3000");
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
        assertThat(preflight.apply("https://evil.example").statusCode()).isEqualTo(403);
    }
}
