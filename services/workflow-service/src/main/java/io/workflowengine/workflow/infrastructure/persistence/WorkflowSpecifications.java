package io.workflowengine.workflow.infrastructure.persistence;

import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.WorkflowStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable filters for the workflow list (Specification pattern). Visibility, status and name search are combined
 * per request instead of writing one query per filter combination.
 */
final class WorkflowSpecifications {

    private WorkflowSpecifications() {}

    static Specification<WorkflowEntity> visibleTo(Actor actor) {
        if (actor.admin()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            Subquery<Integer> shared = query.subquery(Integer.class);
            var sub = shared.from(WorkflowEntity.class);
            Join<WorkflowEntity, PermissionEmbeddable> permission = sub.join("permissions");
            shared.select(cb.literal(1))
                    .where(cb.equal(sub.get("id"), root.get("id")), cb.equal(permission.get("userId"), actor.userId()));
            return cb.or(cb.equal(root.get("ownerId"), actor.userId()), cb.exists(shared));
        };
    }

    static Specification<WorkflowEntity> hasStatus(WorkflowStatus status) {
        if (status == null) {
            return (root, query, cb) -> cb.notEqual(root.get("status"), WorkflowStatus.ARCHIVED.name());
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status.name());
    }

    static Specification<WorkflowEntity> nameContains(String search) {
        if (search == null || search.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + search.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }
}
