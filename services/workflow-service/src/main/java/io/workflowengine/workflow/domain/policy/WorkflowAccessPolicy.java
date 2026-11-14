package io.workflowengine.workflow.domain.policy;

import io.workflowengine.workflow.domain.exception.WorkflowAccessDeniedException;
import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.Workflow;
import io.workflowengine.workflow.domain.model.WorkflowAction;
import io.workflowengine.workflow.domain.model.WorkflowRole;
import java.util.Optional;

/**
 * Workflow-level authorization. Platform admins may do anything; otherwise the caller's role on the workflow must
 * include the action's minimum role.
 */
public final class WorkflowAccessPolicy {

    private WorkflowAccessPolicy() {}

    public static Optional<WorkflowRole> effectiveRole(Workflow workflow, Actor actor) {
        if (workflow.ownerId().equals(actor.userId())) {
            return Optional.of(WorkflowRole.OWNER);
        }
        Optional<WorkflowRole> granted = workflow.roleOf(actor.userId());
        if (granted.isPresent()) {
            return granted;
        }
        return actor.admin() ? Optional.of(WorkflowRole.OWNER) : Optional.empty();
    }

    public static boolean isAllowed(Workflow workflow, Actor actor, WorkflowAction action) {
        if (actor.admin()) {
            return true;
        }
        return effectiveRole(workflow, actor)
                .map(role -> role.includes(action.minimumRole()))
                .orElse(false);
    }

    public static void require(Workflow workflow, Actor actor, WorkflowAction action) {
        if (!isAllowed(workflow, actor, action)) {
            throw new WorkflowAccessDeniedException(action);
        }
    }
}
