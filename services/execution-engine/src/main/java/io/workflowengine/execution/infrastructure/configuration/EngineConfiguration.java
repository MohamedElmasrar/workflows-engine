package io.workflowengine.execution.infrastructure.configuration;

import io.workflowengine.contracts.Topics;
import io.workflowengine.execution.application.port.out.ExecutionEffectPublisher;
import io.workflowengine.execution.application.port.out.ExecutionMetrics;
import io.workflowengine.execution.application.port.out.ExecutionReadModel;
import io.workflowengine.execution.application.port.out.ExecutionRepository;
import io.workflowengine.execution.application.port.out.MessageDeduplicator;
import io.workflowengine.execution.application.port.out.TransactionRunner;
import io.workflowengine.execution.application.port.out.WorkflowCatalog;
import io.workflowengine.execution.application.usecase.ExecutionQueryService;
import io.workflowengine.execution.application.usecase.ExecutionService;
import io.workflowengine.execution.application.usecase.WorkflowProjectionService;
import io.workflowengine.execution.domain.model.EngineSettings;
import io.workflowengine.platform.messaging.MessagingProperties;
import java.time.Clock;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
public class EngineConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EngineSettings engineSettings(@Value("${engine.lease-grace:60s}") Duration leaseGrace) {
        return new EngineSettings(leaseGrace);
    }

    @Bean
    ExecutionService executionService(
            ExecutionRepository executions,
            WorkflowCatalog catalog,
            ExecutionEffectPublisher publisher,
            ExecutionMetrics metrics,
            TransactionRunner tx,
            MessageDeduplicator deduplicator,
            EngineSettings settings,
            Clock clock) {
        return new ExecutionService(executions, catalog, publisher, metrics, tx, deduplicator, settings, clock);
    }

    @Bean
    WorkflowProjectionService workflowProjectionService(
            WorkflowCatalog catalog, MessageDeduplicator deduplicator, TransactionRunner tx) {
        return new WorkflowProjectionService(catalog, deduplicator, tx);
    }

    @Bean
    ExecutionQueryService executionQueryService(
            ExecutionRepository executions, ExecutionReadModel readModel, WorkflowCatalog catalog, Clock clock) {
        return new ExecutionQueryService(executions, readModel, catalog, clock);
    }

    /** Declares every topic this service produces or consumes, plus dead-letter topics for its consumers. */
    @Bean
    KafkaAdmin.NewTopics engineTopics(MessagingProperties p) {
        return new KafkaAdmin.NewTopics(
                topic(Topics.NODE_TASKS, p),
                topic(Topics.EXECUTION_LIFECYCLE, p),
                topic(Topics.WORKFLOW_EVENTS, p),
                topic(Topics.EXECUTION_REQUESTS, p),
                topic(Topics.NODE_RESULTS, p),
                topic(Topics.deadLetter(Topics.WORKFLOW_EVENTS), p),
                topic(Topics.deadLetter(Topics.EXECUTION_REQUESTS), p),
                topic(Topics.deadLetter(Topics.NODE_RESULTS), p));
    }

    private static NewTopic topic(String name, MessagingProperties p) {
        return TopicBuilder.name(name)
                .partitions(p.getPartitions())
                .replicas(p.getReplicationFactor())
                .build();
    }
}
