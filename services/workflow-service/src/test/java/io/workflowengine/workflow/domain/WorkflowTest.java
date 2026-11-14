package io.workflowengine.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.workflowengine.workflow.domain.event.WorkflowDomainEvent;
import io.workflowengine.workflow.domain.exception.InvalidWorkflowException;
import io.workflowengine.workflow.domain.exception.RevisionConflictException;
import io.workflowengine.workflow.domain.exception.WorkflowAccessDeniedException;
import io.workflowengine.workflow.domain.exception.WorkflowStateException;
import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.Workflow;
import io.workflowengine.workflow.domain.model.WorkflowRole;
import io.workflowengine.workflow.domain.model.WorkflowStatus;
import io.workflowengine.workflow.domain.model.WorkflowVersion;
import io.workflowengine.workflow.domain.service.ValidationIssue;
import io.workflowengine.workflow.domain.service.ValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
    private final Actor owner = new Actor("owner", false);
    private final Actor editor = new Actor("editor", false);
    private final Actor viewer = new Actor("viewer", false);
    private final Actor stranger = new Actor("stranger", false);
    private final Actor admin = new Actor("admin", true);

    private Workflow newWorkflow() {
        Workflow w = Workflow.create(UUID.randomUUID(), "owner", "Demo", null, null, NOW);
        w.grant(owner, "editor", WorkflowRole.EDITOR, NOW);
        w.grant(owner, "viewer", WorkflowRole.VIEWER, NOW);
        w.pullEvents();
        return w;
    }

    @Test
    void creationRecordsInitialAclAndStartsWithManualTrigger() {
        Workflow w = Workflow.create(UUID.randomUUID(), "owner", "Demo", null, null, NOW);
        assertThat(w.status()).isEqualTo(WorkflowStatus.DRAFT);
        assertThat(w.draft().trigger()).isPresent();
        assertThat(w.pullEvents()).singleElement().isInstanceOf(WorkflowDomainEvent.AccessChanged.class);
    }

    @Test
    void updateRequiresCurrentRevision() {
        Workflow w = newWorkflow();
        w.updateDraft(editor, 1, "Renamed", null, w.draft(), NOW);
        assertThat(w.revision()).isEqualTo(2);
        assertThatThrownBy(() -> w.updateDraft(owner, 1, "Again", null, w.draft(), NOW))
                .isInstanceOf(RevisionConflictException.class);
    }

    @Test
    void viewersAndStrangersCannotEdit() {
        Workflow w = newWorkflow();
        assertThatThrownBy(() -> w.updateDraft(viewer, 1, "x", null, w.draft(), NOW))
                .isInstanceOf(WorkflowAccessDeniedException.class);
        assertThatThrownBy(() -> w.updateDraft(stranger, 1, "x", null, w.draft(), NOW))
                .isInstanceOf(WorkflowAccessDeniedException.class);
    }

    @Test
    void publishCreatesIncrementingVersionsAndEvent() {
        Workflow w = newWorkflow();
        WorkflowVersion v1 = w.publish(editor, ValidationResult.ok(), NOW);
        WorkflowVersion v2 = w.publish(editor, ValidationResult.ok(), NOW);
        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(w.status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(w.hasUnpublishedChanges()).isFalse();
        assertThat(w.pullEvents()).hasSize(2).allMatch(e -> e instanceof WorkflowDomainEvent.VersionPublished);
    }

    @Test
    void publishRefusesInvalidDefinitions() {
        Workflow w = newWorkflow();
        ValidationResult invalid =
                new ValidationResult(List.of(ValidationIssue.general("MISSING_TRIGGER", "no trigger")));
        assertThatThrownBy(() -> w.publish(owner, invalid, NOW)).isInstanceOf(InvalidWorkflowException.class);
        assertThat(w.latestVersion()).isNull();
    }

    @Test
    void onlyOwnerManagesPermissionsAndArchives() {
        Workflow w = newWorkflow();
        assertThatThrownBy(() -> w.grant(editor, "x", WorkflowRole.VIEWER, NOW))
                .isInstanceOf(WorkflowAccessDeniedException.class);
        assertThatThrownBy(() -> w.archive(editor, NOW)).isInstanceOf(WorkflowAccessDeniedException.class);
        assertThatThrownBy(() -> w.grant(owner, "x", WorkflowRole.OWNER, NOW))
                .isInstanceOf(WorkflowStateException.class);
    }

    @Test
    void adminsMayDoEverything() {
        Workflow w = newWorkflow();
        w.archive(admin, NOW);
        assertThat(w.status()).isEqualTo(WorkflowStatus.ARCHIVED);
    }

    @Test
    void archivedWorkflowsAreReadOnlyAndEmitAccessUpdate() {
        Workflow w = newWorkflow();
        w.archive(owner, NOW);
        assertThat(w.pullEvents())
                .hasSize(2)
                .anyMatch(e -> e instanceof WorkflowDomainEvent.Archived)
                .anyMatch(e -> e instanceof WorkflowDomainEvent.AccessChanged a && a.archived());
        assertThatThrownBy(() -> w.updateDraft(owner, 1, "x", null, w.draft(), NOW))
                .isInstanceOf(WorkflowStateException.class);
        assertThatThrownBy(() -> w.publish(owner, ValidationResult.ok(), NOW))
                .isInstanceOf(WorkflowStateException.class);
    }

    @Test
    void aclRevisionIncreasesWithEveryChange() {
        Workflow w = newWorkflow();
        long before = w.aclRevision();
        w.grant(owner, "editor", WorkflowRole.VIEWER, NOW);
        w.revoke(owner, "viewer");
        assertThat(w.aclRevision()).isEqualTo(before + 2);
        assertThat(w.roleOf("editor")).contains(WorkflowRole.VIEWER);
        assertThat(w.roleOf("viewer")).isEmpty();
    }

    @Test
    void duplicateIsOwnedByCaller() {
        Workflow copy = newWorkflow().duplicate(viewer, UUID.randomUUID(), NOW);
        assertThat(copy.ownerId()).isEqualTo("viewer");
        assertThat(copy.name()).isEqualTo("Demo (copy)");
        assertThat(copy.permissions()).isEmpty();
    }
}
