package io.workflowengine.trigger.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.contracts.Topics;
import io.workflowengine.platform.messaging.MessagingProperties;
import io.workflowengine.trigger.application.port.out.AccessRepository;
import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import io.workflowengine.trigger.application.port.out.ExecutionRequestPublisher;
import io.workflowengine.trigger.application.port.out.MessageDeduplicator;
import io.workflowengine.trigger.application.port.out.SecretProtector;
import io.workflowengine.trigger.application.port.out.TriggerRepository;
import io.workflowengine.trigger.application.usecase.Authorization;
import io.workflowengine.trigger.application.usecase.ScheduleService;
import io.workflowengine.trigger.application.usecase.TriggerQueryService;
import io.workflowengine.trigger.application.usecase.TriggerRegistrationService;
import io.workflowengine.trigger.application.usecase.WebhookService;
import io.workflowengine.trigger.domain.service.SchedulePolicy;
import io.workflowengine.trigger.infrastructure.crypto.AesGcmSecretProtector;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.support.CronExpression;

@Configuration(proxyBeanMethods = false)
public class TriggerConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** 5-field cron evaluated with Spring's CronExpression (a seconds field of 0 is prefixed). */
    @Bean
    SchedulePolicy schedulePolicy() {
        return new SchedulePolicy(
                (cron, after) -> CronExpression.parse("0 " + cron.strip()).next(after));
    }

    @Bean
    SecretProtector secretProtector(@Value("${trigger.encryption-key:}") String key) {
        return new AesGcmSecretProtector(key);
    }

    @Bean
    Authorization authorization(AccessRepository access) {
        return new Authorization(access);
    }

    @Bean
    TriggerRegistrationService triggerRegistrationService(
            TriggerRepository triggers,
            AccessRepository access,
            DeliveryRepository deliveries,
            SecretProtector secrets,
            MessageDeduplicator dedup,
            SchedulePolicy policy,
            Clock clock) {
        return new TriggerRegistrationService(triggers, access, deliveries, secrets, dedup, policy, clock);
    }

    @Bean
    WebhookService webhookService(
            TriggerRepository triggers,
            DeliveryRepository deliveries,
            SecretProtector secrets,
            ExecutionRequestPublisher publisher,
            ObjectMapper objectMapper,
            Clock clock) {
        return new WebhookService(triggers, deliveries, secrets, publisher, objectMapper, clock);
    }

    @Bean
    ScheduleService scheduleService(
            TriggerRepository triggers,
            DeliveryRepository deliveries,
            ExecutionRequestPublisher publisher,
            SchedulePolicy policy,
            ObjectMapper objectMapper,
            Clock clock) {
        return new ScheduleService(triggers, deliveries, publisher, policy, objectMapper, clock);
    }

    @Bean
    TriggerQueryService triggerQueryService(
            TriggerRepository triggers,
            DeliveryRepository deliveries,
            SecretProtector secrets,
            Authorization authorization,
            @Value("${trigger.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        return new TriggerQueryService(triggers, deliveries, secrets, authorization, publicBaseUrl);
    }

    @Bean
    KafkaAdmin.NewTopics triggerTopics(MessagingProperties p) {
        return new KafkaAdmin.NewTopics(
                topic(Topics.EXECUTION_REQUESTS, p),
                topic(Topics.WORKFLOW_EVENTS, p),
                topic(Topics.EXECUTION_LIFECYCLE, p),
                topic(Topics.deadLetter(Topics.WORKFLOW_EVENTS), p),
                topic(Topics.deadLetter(Topics.EXECUTION_LIFECYCLE), p));
    }

    private static NewTopic topic(String name, MessagingProperties p) {
        return TopicBuilder.name(name)
                .partitions(p.getPartitions())
                .replicas(p.getReplicationFactor())
                .build();
    }
}
