package io.workflowengine.workflow.infrastructure.configuration;

import io.workflowengine.contracts.Topics;
import io.workflowengine.platform.messaging.MessagingProperties;
import io.workflowengine.workflow.application.port.out.DefinitionSchemaValidator;
import io.workflowengine.workflow.application.port.out.IntegrationEventPublisher;
import io.workflowengine.workflow.application.port.out.UserDirectory;
import io.workflowengine.workflow.application.port.out.WorkflowRepository;
import io.workflowengine.workflow.application.port.out.WorkflowVersionRepository;
import io.workflowengine.workflow.application.usecase.WorkflowCommandService;
import io.workflowengine.workflow.application.usecase.WorkflowQueryService;
import io.workflowengine.workflow.application.usecase.WorkflowValidation;
import io.workflowengine.workflow.domain.service.GraphValidator;
import io.workflowengine.workflow.domain.service.ScheduleRules;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Wires framework-free domain and application classes. */
@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GraphValidator graphValidator(ScheduleRules scheduleRules) {
        return new GraphValidator(scheduleRules);
    }

    @Bean
    WorkflowValidation workflowValidation(DefinitionSchemaValidator schemaValidator, GraphValidator graphValidator) {
        return new WorkflowValidation(schemaValidator, graphValidator);
    }

    @Bean
    WorkflowCommandService workflowCommandService(
            WorkflowRepository workflows,
            WorkflowVersionRepository versions,
            WorkflowValidation validation,
            IntegrationEventPublisher events,
            UserDirectory users,
            Clock clock) {
        return new WorkflowCommandService(workflows, versions, validation, events, users, clock);
    }

    @Bean
    WorkflowQueryService workflowQueryService(
            WorkflowRepository workflows,
            WorkflowVersionRepository versions,
            WorkflowValidation validation,
            UserDirectory users) {
        return new WorkflowQueryService(workflows, versions, validation, users);
    }

    @Bean
    NewTopic workflowEventsTopic(MessagingProperties properties) {
        return TopicBuilder.name(Topics.WORKFLOW_EVENTS)
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicationFactor())
                .build();
    }
}
