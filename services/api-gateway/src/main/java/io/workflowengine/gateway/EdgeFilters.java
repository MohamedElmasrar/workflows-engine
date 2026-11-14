package io.workflowengine.gateway;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class EdgeFilters {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /** Assigns a correlation id to every request (keeping a well-formed incoming one) and returns it to the client. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    GlobalFilter correlationIdFilter() {
        return (exchange, chain) -> {
            String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
            String id = incoming != null && SAFE.matcher(incoming).matches()
                    ? incoming
                    : UUID.randomUUID().toString();
            ServerHttpRequest request = exchange.getRequest()
                    .mutate()
                    .headers(h -> h.set(CORRELATION_HEADER, id))
                    .build();
            exchange.getResponse().getHeaders().set(CORRELATION_HEADER, id);
            return chain.filter(exchange.mutate().request(request).build());
        };
    }

    /** Rate-limit key: the authenticated user, or the client address for anonymous requests (webhooks). */
    @Bean
    @Primary
    KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(p -> "user:" + p.getName())
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange.getRequest())));
    }

    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange.getRequest()));
    }

    private static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null
                ? "unknown"
                : remote.getAddress().getHostAddress();
    }
}
