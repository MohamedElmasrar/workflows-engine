package io.workflowengine.worker.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.port.HttpGateway;
import io.workflowengine.worker.infrastructure.http.SafeHttpGateway;
import io.workflowengine.worker.infrastructure.http.SsrfPolicy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafeHttpGatewayTest {

    static HttpServer server;
    static int port;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/big", ex -> {
            byte[] body = new byte[2048];
            ex.sendResponseHeaders(200, 0); // chunked: size unknown up front
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/to-metadata", ex -> {
            ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/to-ok", ex -> {
            ex.getResponseHeaders().add("Location", "/ok");
            ex.sendResponseHeaders(303, -1);
            ex.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static SafeHttpGateway gateway(Set<String> allowed) {
        return new SafeHttpGateway(
                new SsrfPolicy(allowed), 1024, Duration.ofSeconds(2), CircuitBreakerRegistry.ofDefaults());
    }

    private static HttpGateway.HttpCall get(String url) {
        return new HttpGateway.HttpCall("GET", url, Map.of(), null, Duration.ofSeconds(2));
    }

    private static String code(Runnable r) {
        try {
            r.run();
            return "none";
        } catch (NodeExecutionException e) {
            return e.code();
        }
    }

    @Test
    void loopbackIsBlockedUnlessAllowListedByAnAdministrator() {
        assertThat(code(() -> gateway(Set.of()).send(get("http://localhost:" + port + "/ok"))))
                .isEqualTo("DESTINATION_BLOCKED");
        assertThat(code(() -> gateway(Set.of()).send(get("http://127.0.0.1:" + port + "/ok"))))
                .isEqualTo("DESTINATION_BLOCKED");
        HttpGateway.HttpResponseData ok = gateway(Set.of("localhost")).send(get("http://localhost:" + port + "/ok"));
        assertThat(ok.status()).isEqualTo(200);
    }

    @Test
    void redirectsAreFollowedAndEachHopIsRevalidated() {
        SafeHttpGateway gw = gateway(Set.of("localhost"));
        assertThat(gw.send(get("http://localhost:" + port + "/to-ok")).status()).isEqualTo(200);
        assertThat(code(() -> gw.send(get("http://localhost:" + port + "/to-metadata"))))
                .isEqualTo("DESTINATION_BLOCKED");
    }

    @Test
    void responseSizeIsLimitedEvenWithoutContentLength() {
        assertThat(code(() -> gateway(Set.of("localhost")).send(get("http://localhost:" + port + "/big"))))
                .isEqualTo("RESPONSE_TOO_LARGE");
    }

    @Test
    void nonHttpSchemesAndUserInfoAreRejected() {
        assertThat(code(() -> gateway(Set.of()).send(get("file:///etc/passwd"))))
                .isEqualTo("DESTINATION_BLOCKED");
        assertThat(code(() -> gateway(Set.of()).send(get("http://user:pw@example.com/"))))
                .isEqualTo("DESTINATION_BLOCKED");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1",
                "10.1.2.3",
                "172.16.0.1",
                "192.168.1.1",
                "169.254.169.254",
                "100.64.0.1",
                "0.0.0.0",
                "::1",
                "fd00::1",
                "fe80::1",
                "::ffff:127.0.0.1",
                "224.0.0.1"
            })
    void ssrfPolicyBlocksInternalRanges(String address) throws Exception {
        assertThat(new SsrfPolicy(Set.of()).isBlocked(InetAddress.getByName(address)))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"})
    void ssrfPolicyAllowsPublicAddresses(String address) throws Exception {
        assertThat(new SsrfPolicy(Set.of()).isBlocked(InetAddress.getByName(address)))
                .isFalse();
    }

    @Test
    void connectionFailuresAreRetryable() {
        assertThatThrownBy(() -> gateway(Set.of("localhost")).send(get("http://localhost:1/nothing")))
                .isInstanceOfSatisfying(NodeExecutionException.class, e -> {
                    assertThat(e.code()).isEqualTo("HTTP_CONNECTION_ERROR");
                    assertThat(e.retryable()).isTrue();
                });
    }
}
