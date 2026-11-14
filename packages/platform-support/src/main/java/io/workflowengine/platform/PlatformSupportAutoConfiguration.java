package io.workflowengine.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.KafkaErrorHandling;
import io.workflowengine.platform.messaging.MessagingProperties;
import io.workflowengine.platform.messaging.ProcessedMessageStore;
import io.workflowengine.platform.outbox.OutboxProperties;
import io.workflowengine.platform.outbox.OutboxPublisher;
import io.workflowengine.platform.outbox.OutboxWriter;
import io.workflowengine.platform.outbox.TraceContextCapture;
import io.workflowengine.platform.security.KeycloakJwtAuthenticationConverter;
import io.workflowengine.platform.web.CorrelationIdFilter;
import io.workflowengine.platform.web.ProblemDetailsAdvice;
import io.workflowengine.platform.web.RequestSizeLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration(
        after = {
            KafkaAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class
        },
        afterName = "org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration")
@EnableConfigurationProperties({MessagingProperties.class, OutboxProperties.class})
public class PlatformSupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EventCodec eventCodec(ObjectMapper objectMapper) {
        return new EventCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template, MessagingProperties properties) {
        return KafkaErrorHandling.errorHandler(template, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    TraceContextCapture traceContextCapture(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        Tracer t = tracer.getIfAvailable();
        Propagator p = propagator.getIfAvailable();
        return t != null && p != null ? new TraceContextCapture(t, p) : TraceContextCapture.noop();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @EnableScheduling
    static class OutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OutboxWriter outboxWriter(
                JdbcTemplate jdbcTemplate, EventCodec codec, ObjectMapper objectMapper, TraceContextCapture trace) {
            return new OutboxWriter(jdbcTemplate, codec, objectMapper, trace);
        }

        @Bean
        @ConditionalOnMissingBean
        ProcessedMessageStore processedMessageStore(JdbcTemplate jdbcTemplate) {
            return new ProcessedMessageStore(jdbcTemplate);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(KafkaTemplate.class)
        @ConditionalOnProperty(value = "platform.outbox.publisher-enabled", matchIfMissing = true)
        @SuppressWarnings("unchecked")
        OutboxPublisher outboxPublisher(
                JdbcTemplate jdbcTemplate,
                TransactionTemplate transactionTemplate,
                KafkaTemplate<?, ?> kafkaTemplate,
                OutboxProperties properties,
                ObjectMapper objectMapper,
                TraceContextCapture trace,
                MeterRegistry meterRegistry) {
            return new OutboxPublisher(
                    jdbcTemplate,
                    transactionTemplate,
                    (KafkaTemplate<String, String>) kafkaTemplate,
                    properties,
                    objectMapper,
                    trace,
                    meterRegistry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfiguration {

        @Bean
        FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
            FilterRegistrationBean<CorrelationIdFilter> registration =
                    new FilterRegistrationBean<>(new CorrelationIdFilter());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }

        @Bean
        FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
                @org.springframework.beans.factory.annotation.Value("${platform.web.max-request-bytes:1048576}")
                        long maxBytes) {
            FilterRegistrationBean<RequestSizeLimitFilter> registration =
                    new FilterRegistrationBean<>(new RequestSizeLimitFilter(maxBytes));
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            return registration;
        }

        @Bean
        @ConditionalOnMissingBean
        ProblemDetailsAdvice problemDetailsAdvice() {
            return new ProblemDetailsAdvice();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.Jwt")
    static class SecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean
        KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
            return new KeycloakJwtAuthenticationConverter();
        }
    }
}
