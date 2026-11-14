package io.workflowengine.workflow.application.port.out;

import io.workflowengine.workflow.application.query.PageResult;
import io.workflowengine.workflow.application.query.WorkflowSearchCriteria;
import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.Workflow;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository {

    Optional<Workflow> findById(UUID id);

    void save(Workflow workflow);

    /** Workflows visible to the actor (owned, shared with them, or all for admins), filtered and paginated. */
    PageResult<Workflow> search(WorkflowSearchCriteria criteria, Actor actor);

    long countVisible(Actor actor, boolean activeOnly);
}
