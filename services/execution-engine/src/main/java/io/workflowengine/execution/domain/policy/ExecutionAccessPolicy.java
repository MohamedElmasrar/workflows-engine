package io.workflowengine.execution.domain.policy;

import io.workflowengine.execution.domain.exception.ExecutionAccessDeniedException;
import io.workflowengine.execution.domain.model.Actor;
import io.workflowengine.execution.domain.model.WorkflowAccess;

/**
 * Same role semantics as the workflow service (OWNER > EDITOR > VIEWER), applied to the local ACL projection:
 * viewers may inspect executions, editors and owners may start, cancel and retry them.
 */
public final class ExecutionAccessPolicy {

    private ExecutionAccessPolicy() {}

    public static boolean canView(WorkflowAccess access, Actor actor) {
        return actor.admin() || (access != null && access.roleOf(actor.userId()) != null);
    }

    public static boolean canOperate(WorkflowAccess access, Actor actor) {
        if (actor.admin()) {
            return true;
        }
        if (access == null) {
            return false;
        }
        String role = access.roleOf(actor.userId());
        return "OWNER".equals(role) || "EDITOR".equals(role);
    }

    public static void requireOperate(WorkflowAccess access, Actor actor) {
        if (!canOperate(access, actor)) {
            throw new ExecutionAccessDeniedException("Only owners and editors may run, cancel or retry this workflow");
        }
    }
}
