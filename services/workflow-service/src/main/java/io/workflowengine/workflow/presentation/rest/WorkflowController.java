package io.workflowengine.workflow.presentation.rest;

import io.workflowengine.platform.security.CurrentUser;
import io.workflowengine.workflow.application.command.WorkflowCommands;
import io.workflowengine.workflow.application.query.PageResult;
import io.workflowengine.workflow.application.query.WorkflowSearchCriteria;
import io.workflowengine.workflow.application.usecase.WorkflowCommandService;
import io.workflowengine.workflow.application.usecase.WorkflowQueryService;
import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.Permission;
import io.workflowengine.workflow.domain.model.Workflow;
import io.workflowengine.workflow.domain.model.WorkflowRole;
import io.workflowengine.workflow.domain.model.WorkflowStatus;
import io.workflowengine.workflow.domain.model.WorkflowVersion;
import io.workflowengine.workflow.domain.policy.WorkflowAccessPolicy;
import io.workflowengine.workflow.domain.service.ValidationResult;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.CreateWorkflowRequest;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.GrantPermissionRequest;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.PageResponse;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.PermissionResponse;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.StatsResponse;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.UpdateWorkflowRequest;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.ValidateRequest;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.ValidationResponse;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.WorkflowResponse;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.WorkflowSummaryResponse;
import io.workflowengine.workflow.presentation.dto.WorkflowDtos.WorkflowVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
@Tag(name = "Workflows", description = "Workflow drafts, validation, publication and sharing")
public class WorkflowController {

    private final WorkflowCommandService commands;
    private final WorkflowQueryService queries;

    public WorkflowController(WorkflowCommandService commands, WorkflowQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Create a workflow draft")
    public ResponseEntity<WorkflowResponse> create(Authentication auth, @Valid @RequestBody CreateWorkflowRequest body) {
        Actor actor = actor(auth);
        Workflow workflow =
                commands.create(actor, new WorkflowCommands.CreateWorkflow(body.name(), body.description(), body.definition()));
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + workflow.id())).body(toResponse(workflow, actor));
    }

    @GetMapping
    @Operation(summary = "List visible workflows with search, status filter, sorting and pagination")
    public PageResponse<WorkflowSummaryResponse> list(
            Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkflowStatus status,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Actor actor = actor(auth);
        PageResult<Workflow> result = queries.search(
                actor, new WorkflowSearchCriteria(search, status, sort, "asc".equalsIgnoreCase(direction), page, size));
        return new PageResponse<>(
                result.items().stream().map(w -> toSummary(w, actor)).toList(),
                result.page(),
                result.size(),
                result.totalItems(),
                result.totalPages());
    }

    @GetMapping("/stats")
    @Operation(summary = "Workflow counts for the dashboard")
    public StatsResponse stats(Authentication auth) {
        WorkflowQueryService.Stats stats = queries.stats(actor(auth));
        return new StatsResponse(stats.total(), stats.active());
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(Authentication auth, @PathVariable UUID id) {
        Actor actor = actor(auth);
        return toResponse(queries.get(actor, id), actor);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update the draft; fails with 409 if 'revision' is stale")
    public WorkflowResponse update(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody UpdateWorkflowRequest body) {
        Actor actor = actor(auth);
        Workflow workflow = commands.update(
                actor,
                new WorkflowCommands.UpdateWorkflow(id, body.revision(), body.name(), body.description(), body.definition()));
        return toResponse(workflow, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive the workflow (soft delete; versions and executions are kept)")
    public void archive(Authentication auth, @PathVariable UUID id) {
        commands.archive(actor(auth), id);
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<WorkflowResponse> duplicate(Authentication auth, @PathVariable UUID id) {
        Actor actor = actor(auth);
        Workflow copy = commands.duplicate(actor, id);
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + copy.id())).body(toResponse(copy, actor));
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate the stored draft, or the definition in the request body")
    public ValidationResponse validate(
            Authentication auth, @PathVariable UUID id, @RequestBody(required = false) ValidateRequest body) {
        ValidationResult result = queries.validate(actor(auth), id, body == null ? null : body.definition());
        return new ValidationResponse(result.valid(), result.errors());
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish the draft as a new immutable version (422 if invalid)")
    public ResponseEntity<WorkflowVersionResponse> publish(Authentication auth, @PathVariable UUID id) {
        WorkflowVersion version = commands.publish(actor(auth), id);
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + id + "/versions/" + version.version()))
                .body(toVersion(version, true));
    }

    @GetMapping("/{id}/versions")
    public List<WorkflowVersionResponse> versions(Authentication auth, @PathVariable UUID id) {
        return queries.versions(actor(auth), id).stream().map(v -> toVersion(v, false)).toList();
    }

    @GetMapping("/{id}/versions/{version}")
    public WorkflowVersionResponse version(Authentication auth, @PathVariable UUID id, @PathVariable int version) {
        return toVersion(queries.version(actor(auth), id, version), true);
    }

    @GetMapping("/{id}/permissions")
    public List<PermissionResponse> permissions(Authentication auth, @PathVariable UUID id) {
        return toPermissions(queries.get(actor(auth), id));
    }

    @PutMapping("/{id}/permissions/{username}")
    @Operation(summary = "Share the workflow with a user (EDITOR or VIEWER)")
    public List<PermissionResponse> grant(
            Authentication auth,
            @PathVariable UUID id,
            @PathVariable String username,
            @Valid @RequestBody GrantPermissionRequest body) {
        return toPermissions(commands.grant(actor(auth), new WorkflowCommands.GrantPermission(id, username, body.role())));
    }

    @DeleteMapping("/{id}/permissions/{username}")
    public List<PermissionResponse> revoke(Authentication auth, @PathVariable UUID id, @PathVariable String username) {
        return toPermissions(commands.revoke(actor(auth), new WorkflowCommands.RevokePermission(id, username)));
    }

    static Actor actor(Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return new Actor(user.id(), user.admin());
    }

    private List<PermissionResponse> toPermissions(Workflow workflow) {
        Collection<Permission> permissions = workflow.permissions();
        List<String> ids = new java.util.ArrayList<>(permissions.stream().map(Permission::userId).toList());
        ids.add(workflow.ownerId());
        Map<String, String> names = queries.usernames(ids);
        List<PermissionResponse> result = new java.util.ArrayList<>();
        result.add(new PermissionResponse(
                workflow.ownerId(), names.get(workflow.ownerId()), WorkflowRole.OWNER, workflow.createdAt()));
        permissions.forEach(p -> result.add(new PermissionResponse(p.userId(), names.get(p.userId()), p.role(), p.grantedAt())));
        return result;
    }

    private static WorkflowResponse toResponse(Workflow w, Actor actor) {
        return new WorkflowResponse(
                w.id(),
                w.name(),
                w.description(),
                w.status(),
                w.ownerId(),
                WorkflowAccessPolicy.effectiveRole(w, actor).orElse(null),
                w.revision(),
                w.latestVersion(),
                w.hasUnpublishedChanges(),
                w.draft().document(),
                w.createdAt(),
                w.updatedAt());
    }

    private static WorkflowSummaryResponse toSummary(Workflow w, Actor actor) {
        return new WorkflowSummaryResponse(
                w.id(),
                w.name(),
                w.description(),
                w.status(),
                w.ownerId(),
                WorkflowAccessPolicy.effectiveRole(w, actor).orElse(null),
                w.latestVersion(),
                w.hasUnpublishedChanges(),
                w.draft().trigger().map(n -> n.type()).orElse(null),
                w.draft().nodes().size(),
                w.createdAt(),
                w.updatedAt());
    }

    private static WorkflowVersionResponse toVersion(WorkflowVersion v, boolean withDefinition) {
        return new WorkflowVersionResponse(
                v.workflowId(),
                v.version(),
                v.name(),
                v.publishedBy(),
                v.publishedAt(),
                withDefinition ? v.definition().document() : null);
    }
}
