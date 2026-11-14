package io.workflowengine.gateway;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Rejects unauthenticated API calls at the edge (signature, issuer, audience, expiry). Services validate the token
 * again and apply resource-level authorization; the gateway only reduces load from anonymous traffic.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    /** Single CORS policy for the public API, applied by Spring Security before authentication. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${gateway.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Last-Event-ID", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})
                .authorizeExchange(ex -> ex.pathMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/webhooks/*")
                        .permitAll()
                        .pathMatchers("/api/**")
                        .authenticated()
                        .anyExchange()
                        .denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> {}))
                .build();
    }
}
