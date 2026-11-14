package io.workflowengine.workflow.application.usecase;

import io.workflowengine.workflow.application.command.WorkflowCommands.CreateWorkflow;
import io.workflowengine.workflow.application.command.WorkflowCommands.GrantPermission;
import io.workflowengine.workflow.application.command.WorkflowCommands.RevokePermission;
import io.workflowengine.workflow.application.command.WorkflowCommands.UpdateWorkflow;
import io.workflowengine.workflow.application.port.out.IntegrationEventPublisher;
import io.workflowengine.workflow.application.port.out.UserDirectory;
import io.workflowengine.workflow.application.port.out.WorkflowRepository;
import io.workflowengine.workflow.application.port.out.WorkflowVersionRepository;
import io.workflowengine.workflow.domain.exception.InvalidWorkflowException;
import io.workflowengine.workflow.domain.exception.WorkflowNotFoundException;
import io.workflowengine.workflow.domain.exception.WorkflowStateException;
import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.Workflow;
import io.workflowengine.workflow.domain.model.WorkflowDefinition;
import io.workflowengine.workflow.domain.model.WorkflowVersion;
import io.workflowengine.workflow.domain.service.ValidationResult;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side use cases. Each method is one transaction: aggregate change, version insert and outbox events commit
 * together. Authorization is enforced by the aggregate through {@code WorkflowAccessPolicy}.
 */
@Transactional
public class WorkflowCommandService {

    private final WorkflowRepository workflows;
    private final WorkflowVersionRepository versions;
    private final WorkflowValidation validation;
    private final IntegrationEventPublisher events;
    private final UserDirectory users;
    private final Clock clock;

    public WorkflowCommandService(
            WorkflowRepository workflows,
            WorkflowVersionRepository versions,
            WorkflowValidation validation,
            IntegrationEventPublisher events,
            UserDirectory users,
            Clock clock) {
        this.workflows = workflows;
        this.versions = versions;
        this.validation = validation;
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    public Workflow create(Actor actor, CreateWorkflow command) {
        WorkflowDefinition draft = null;
        if (command.definition() != null) {
            draft = structurallyValid(command.definition());
        }
        Workflow workflow = Workflow.create(
                UUID.randomUUID(), actor.userId(), command.name(), command.description(), draft, clock.instant());
        return persist(workflow);
    }

    public Workflow update(Actor actor, UpdateWorkflow command) {
        Workflow workflow = load(command.workflowId());
        workflow.updateDraft(
                actor,
                command.expectedRevision(),
                command.name(),
                command.description(),
                structurallyValid(command.definition()),
                clock.instant());
        return persist(workflow);
    }

    public WorkflowVersion publish(Actor actor, UUID workflowId) {
        Workflow workflow = load(workflowId);
        ValidationResult result = validation.validate(workflow.draft().document());
        WorkflowVersion version = workflow.publish(actor, result, clock.instant());
        versions.insert(version);
        persist(workflow);
        return version;
    }

    public void archive(Actor actor, UUID workflowId) {
        Workflow workflow = load(workflowId);
        workflow.archive(actor, clock.instant());
        persist(workflow);
    }

    public Workflow duplicate(Actor actor, UUID workflowId) {
        Workflow copy = load(workflowId).duplicate(actor, UUID.randomUUID(), clock.instant());
        return persist(copy);
    }

    public Workflow grant(Actor actor, GrantPermission command) {
        Workflow workflow = load(command.workflowId());
        String userId = users.findIdByUsername(command.username())
                .orElseThrow(() -> new WorkflowStateException("Unknown user '" + command.username()
                        + "'. Users must sign in once before they can be added."));
        workflow.grant(actor, userId, command.role(), clock.instant());
        return persist(workflow);
    }

    public Workflow revoke(Actor actor, RevokePermission command) {
        Workflow workflow = load(command.workflowId());
        users.findIdByUsername(command.username()).ifPresent(userId -> workflow.revoke(actor, userId));
        return persist(workflow);
    }

    private Workflow persist(Workflow workflow) {
        workflows.save(workflow);
        events.publish(workflow.pullEvents());
        return workflow;
    }

    private Workflow load(UUID id) {
        return workflows.findById(id).orElseThrow(() -> new WorkflowNotFoundException(id));
    }

    /** Drafts may be semantically incomplete, but must match the schema so they stay loadable by the editor. */
    private WorkflowDefinition structurallyValid(com.fasterxml.jackson.databind.JsonNode document) {
        if (document == null) {
            throw new IllegalArgumentException("definition is required");
        }
        ValidationResult structural = validation.validate(document);
        boolean schemaErrors =
                structural.errors().stream().anyMatch(e -> e.code().equals("SCHEMA_VIOLATION"));
        if (schemaErrors) {
            throw new InvalidWorkflowException(structural);
        }
        return WorkflowDefinition.of(document);
    }
}
