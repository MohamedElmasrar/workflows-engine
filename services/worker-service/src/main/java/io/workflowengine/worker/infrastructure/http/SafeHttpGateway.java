package io.workflowengine.worker.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.port.HttpGateway;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * Outbound HTTP adapter (Apache HttpClient 5). Enforces: http(s) only, SSRF policy on every hop (DNS resolver plus
 * literal-IP check), manual redirect handling (max 5 hops, each re-validated), response size limit, no proxies, no
 * cookies, and a circuit breaker per destination host. Request headers and bodies are never logged.
 */
@Slf4j
public class SafeHttpGateway implements HttpGateway {
    private static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    private final CloseableHttpClient client;
    private final SsrfPolicy policy;
    private final long maxResponseBytes;
    private final CircuitBreakerRegistry breakers;

    public SafeHttpGateway(
            SsrfPolicy policy, long maxResponseBytes, Duration connectTimeout, CircuitBreakerRegistry breakers) {
        this.policy = policy;
        this.maxResponseBytes = maxResponseBytes;
        this.breakers = breakers;
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new GuardedDnsResolver(policy))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connectTimeout))
                                .build())
                        .setMaxConnTotal(200)
                        .setMaxConnPerRoute(20)
                        .build())
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAutomaticRetries()
                .disableAuthCaching()
                .setUserAgent("workflow-engine-worker/0.1")
                .build();
    }

    @Override
    public HttpResponseData send(HttpCall call) {
        String method = call.method();
        URI uri = parse(call.url());
        byte[] body = call.body();
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkDestination(uri);
            HttpResponseData response = sendOnce(method, uri, call.headers(), body, call.timeout());
            if (!REDIRECTS.contains(response.status())) {
                return response;
            }
            String location = first(response.headers(), "location");
            if (location == null) {
                return response;
            }
            uri = uri.resolve(location);
            if (response.status() == 303
                    || ((response.status() == 301 || response.status() == 302) && !method.equals("GET"))) {
                method = "GET";
                body = null;
            }
        }
        throw new NodeExecutionException("TOO_MANY_REDIRECTS", "More than " + MAX_REDIRECTS + " redirects", false);
    }

    private HttpResponseData sendOnce(
            String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) {
        CircuitBreaker breaker = breakers.circuitBreaker("http:" + uri.getHost().toLowerCase());
        try {
            return breaker.executeCallable(() -> {
                HttpResponseData response = execute(method, uri, headers, body, timeout);
                if (response.status() >= 500) {
                    // count server errors as failures for the breaker without losing the response
                    throw new ServerErrorResponse(response);
                }
                return response;
            });
        } catch (ServerErrorResponse e) {
            return e.response;
        } catch (CallNotPermittedException e) {
            throw new NodeExecutionException(
                    "CIRCUIT_OPEN", "Circuit breaker for " + uri.getHost() + " is open after repeated failures", true);
        } catch (GuardedDnsResolver.DestinationBlockedException e) {
            throw new NodeExecutionException("DESTINATION_BLOCKED", e.getMessage(), false);
        } catch (NodeExecutionException e) {
            throw e;
        } catch (IOException e) {
            log.info(
                    "HTTP {} {} failed: {}", method, uri.getHost(), e.getClass().getSimpleName());
            throw new NodeExecutionException(
                    "HTTP_CONNECTION_ERROR",
                    "Request to " + uri.getHost() + " failed: " + e.getClass().getSimpleName(),
                    true,
                    e);
        } catch (Exception e) {
            throw new NodeExecutionException("HTTP_CONNECTION_ERROR", "Request failed: " + e.getMessage(), true, e);
        }
    }

    private HttpResponseData execute(String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout)
            throws IOException {
        HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
        headers.forEach(request::setHeader);
        if (body != null) {
            String type = headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase("Content-Type"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("application/json");
            request.setEntity(new ByteArrayEntity(body, ContentType.parse(type)));
        }
        request.setConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.of(timeout))
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .build());
        long started = System.nanoTime();
        return client.execute(request, response -> {
            HttpResponseData data = read(response);
            log.info(
                    "HTTP {} {} -> {} in {} ms",
                    method,
                    uri.getHost(),
                    data.status(),
                    (System.nanoTime() - started) / 1_000_000);
            return data;
        });
    }

    private HttpResponseData read(ClassicHttpResponse response) throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (Header h : response.getHeaders()) {
            headers.computeIfAbsent(h.getName().toLowerCase(), k -> new java.util.ArrayList<>())
                    .add(h.getValue());
        }
        HttpEntity entity = response.getEntity();
        byte[] body = new byte[0];
        String contentType = null;
        if (entity != null) {
            contentType = entity.getContentType();
            if (entity.getContentLength() > maxResponseBytes) {
                throw new NodeExecutionException(
                        "RESPONSE_TOO_LARGE", "Response exceeds " + maxResponseBytes + " bytes", false);
            }
            try (InputStream in = entity.getContent()) {
                body = in.readNBytes((int) maxResponseBytes + 1);
            }
            if (body.length > maxResponseBytes) {
                throw new NodeExecutionException(
                        "RESPONSE_TOO_LARGE", "Response exceeds " + maxResponseBytes + " bytes", false);
            }
        }
        return new HttpResponseData(response.getCode(), headers, body, contentType);
    }

    private void checkDestination(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new NodeExecutionException("DESTINATION_BLOCKED", "Only http and https URLs are allowed", false);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw NodeExecutionException.invalidConfig("URL has no host");
        }
        if (uri.getUserInfo() != null) {
            throw new NodeExecutionException("DESTINATION_BLOCKED", "Credentials in URLs are not allowed", false);
        }
        String literal = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        if (!policy.isAllowListed(host) && (literal.matches("[0-9.]+") || literal.contains(":"))) {
            try {
                if (policy.isBlocked(java.net.InetAddress.getByName(literal))) {
                    throw new NodeExecutionException(
                            "DESTINATION_BLOCKED", "Destination address " + literal + " is not allowed", false);
                }
            } catch (java.net.UnknownHostException e) {
                throw NodeExecutionException.invalidConfig("Invalid host " + host);
            }
        }
    }

    private static URI parse(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            throw NodeExecutionException.invalidConfig("Invalid URL: " + e.getMessage());
        }
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static final class ServerErrorResponse extends RuntimeException {
        private final transient HttpResponseData response;

        ServerErrorResponse(HttpResponseData response) {
            super(null, null, false, false);
            this.response = response;
        }
    }
}
