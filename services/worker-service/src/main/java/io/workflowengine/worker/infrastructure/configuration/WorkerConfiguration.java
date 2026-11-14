package io.workflowengine.worker.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.workflowengine.contracts.Topics;
import io.workflowengine.platform.messaging.EventCodec;
import io.workflowengine.platform.messaging.MessagingProperties;
import io.workflowengine.worker.application.port.out.AttemptCache;
import io.workflowengine.worker.application.port.out.ResultPublisher;
import io.workflowengine.worker.application.port.out.WorkerMetrics;
import io.workflowengine.worker.application.usecase.TaskProcessor;
import io.workflowengine.worker.domain.handler.ConditionHandler;
import io.workflowengine.worker.domain.handler.HttpRequestHandler;
import io.workflowengine.worker.domain.handler.LogHandler;
import io.workflowengine.worker.domain.handler.NodeHandler;
import io.workflowengine.worker.domain.handler.NodeHandlerRegistry;
import io.workflowengine.worker.domain.handler.NotificationHandler;
import io.workflowengine.worker.domain.handler.TransformHandler;
import io.workflowengine.worker.domain.port.HttpGateway;
import io.workflowengine.worker.domain.port.NotificationChannel;
import io.workflowengine.worker.infrastructure.cache.RedisAttemptCache;
import io.workflowengine.worker.infrastructure.http.SafeHttpGateway;
import io.workflowengine.worker.infrastructure.http.SsrfPolicy;
import io.workflowengine.worker.infrastructure.messaging.KafkaResultPublisher;
import io.workflowengine.worker.infrastructure.messaging.TaskListener;
import io.workflowengine.worker.infrastructure.metrics.MicrometerWorkerMetrics;
import io.workflowengine.worker.infrastructure.notification.EmailNotificationChannel;
import io.workflowengine.worker.infrastructure.notification.LogNotificationChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
public class WorkerConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService nodeExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    SsrfPolicy ssrfPolicy(@Value("${worker.http.allowed-hosts:}") Set<String> allowedHosts) {
        Set<String> hosts = new HashSet<>(allowedHosts);
        hosts.remove("");
        return new SsrfPolicy(hosts);
    }

    /** Separate registry so per-destination breakers (user-controlled host names) do not become metric labels. */
    @Bean
    HttpGateway httpGateway(
            SsrfPolicy policy,
            @Value("${worker.http.max-response-bytes:1048576}") long maxResponseBytes,
            @Value("${worker.http.connect-timeout:5s}") Duration connectTimeout) {
        CircuitBreakerRegistry destinations = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        return new SafeHttpGateway(policy, maxResponseBytes, connectTimeout, destinations);
    }

    /** Registry for breakers with fixed names (e.g. smtp); these are exported as metrics. */
    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)
                .bindTo(meterRegistry);
        return registry;
    }

    @Bean
    NotificationChannel emailChannel(
            JavaMailSender sender, CircuitBreakerRegistry registry, @Value("${worker.mail.from:workflows@example.com}") String from) {
        return new EmailNotificationChannel(sender, registry.circuitBreaker("smtp"), from);
    }

    @Bean
    NotificationChannel logChannel() {
        return new LogNotificationChannel();
    }

    @Bean
    NodeHandlerRegistry nodeHandlerRegistry(
            HttpGateway http, List<NotificationChannel> channels, ObjectMapper objectMapper, Clock clock) {
        List<NodeHandler> handlers = List.of(
                new LogHandler(clock),
                new ConditionHandler(),
                new TransformHandler(),
                new HttpRequestHandler(http, objectMapper),
                new NotificationHandler(channels, clock));
        return new NodeHandlerRegistry(handlers);
    }

    @Bean
    AttemptCache attemptCache(StringRedisTemplate redis, @Value("${worker.attempt-cache-ttl:24h}") Duration ttl) {
        return new RedisAttemptCache(redis, ttl);
    }

    @Bean
    ResultPublisher resultPublisher(KafkaTemplate<String, String> kafka, EventCodec codec) {
        return new KafkaResultPublisher(kafka, codec);
    }

    @Bean
    WorkerMetrics workerMetrics(MeterRegistry registry) {
        return new MicrometerWorkerMetrics(registry);
    }

    @Bean
    TaskProcessor taskProcessor(
            NodeHandlerRegistry registry,
            ResultPublisher publisher,
            AttemptCache cache,
            WorkerMetrics metrics,
            ExecutorService nodeExecutor,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${worker.id:${HOSTNAME:worker-local}}") String workerId) {
        return new TaskProcessor(registry, publisher, cache, metrics, nodeExecutor, objectMapper, clock, workerId);
    }

    @Bean
    TaskListener taskListener(EventCodec codec, TaskProcessor processor) {
        return new TaskListener(codec, processor);
    }

    @Bean
    KafkaAdmin.NewTopics workerTopics(MessagingProperties p) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.NODE_TASKS).partitions(p.getPartitions()).replicas(p.getReplicationFactor()).build(),
                TopicBuilder.name(Topics.NODE_RESULTS).partitions(p.getPartitions()).replicas(p.getReplicationFactor()).build(),
                TopicBuilder.name(Topics.deadLetter(Topics.NODE_TASKS)).partitions(p.getPartitions()).replicas(p.getReplicationFactor()).build());
    }
}
