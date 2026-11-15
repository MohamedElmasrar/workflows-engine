package io.workflowengine.workflow.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.workflow.application.port.out.UserDirectory;
import io.workflowengine.workflow.application.port.out.WorkflowRepository;
import io.workflowengine.workflow.application.port.out.WorkflowVersionRepository;
import io.workflowengine.workflow.application.query.PageResult;
import io.workflowengine.workflow.application.query.WorkflowSearchCriteria;
import io.workflowengine.workflow.domain.exception.WorkflowNotFoundException;
import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.Workflow;
import io.workflowengine.workflow.domain.model.WorkflowAction;
import io.workflowengine.workflow.domain.model.WorkflowVersion;
import io.workflowengine.workflow.domain.policy.WorkflowAccessPolicy;
import io.workflowengine.workflow.domain.service.ValidationResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkflowQueryService {

    private final WorkflowRepository workflows;
    private final WorkflowVersionRepository versions;
    private final WorkflowValidation validation;
    private final UserDirectory users;

    public Workflow get(Actor actor, UUID id) {
        Workflow workflow = workflows.findById(id).orElseThrow(() -> new WorkflowNotFoundException(id));
        if (!WorkflowAccessPolicy.isAllowed(workflow, actor, WorkflowAction.VIEW)) {
            // do not reveal the existence of workflows the caller cannot see
            throw new WorkflowNotFoundException(id);
        }
        return workflow;
    }

    public PageResult<Workflow> search(Actor actor, WorkflowSearchCriteria criteria) {
        return workflows.search(criteria, actor);
    }

    public List<WorkflowVersion> versions(Actor actor, UUID id) {
        get(actor, id);
        return versions.findAll(id);
    }

    public WorkflowVersion version(Actor actor, UUID id, int version) {
        get(actor, id);
        return versions.find(id, version).orElseThrow(() -> new WorkflowNotFoundException(id, version));
    }

    /** Validates the stored draft, or a candidate definition sent by the editor before saving. */
    public ValidationResult validate(Actor actor, UUID id, JsonNode candidate) {
        Workflow workflow = get(actor, id);
        return validation.validate(
                candidate != null ? candidate : workflow.draft().document());
    }

    public Stats stats(Actor actor) {
        return new Stats(workflows.countVisible(actor, false), workflows.countVisible(actor, true));
    }

    public Map<String, String> usernames(Collection<String> userIds) {
        return users.usernames(userIds);
    }

    public record Stats(long total, long active) {}
}
