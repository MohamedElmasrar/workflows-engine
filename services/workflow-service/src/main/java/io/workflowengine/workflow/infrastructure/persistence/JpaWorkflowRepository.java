package io.workflowengine.workflow.infrastructure.persistence;

import io.workflowengine.workflow.application.port.out.WorkflowRepository;
import io.workflowengine.workflow.application.query.PageResult;
import io.workflowengine.workflow.application.query.WorkflowSearchCriteria;
import io.workflowengine.workflow.domain.model.Actor;
import io.workflowengine.workflow.domain.model.Permission;
import io.workflowengine.workflow.domain.model.Workflow;
import io.workflowengine.workflow.domain.model.WorkflowDefinition;
import io.workflowengine.workflow.domain.model.WorkflowRole;
import io.workflowengine.workflow.domain.model.WorkflowStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adapter mapping the {@link Workflow} aggregate to JPA entities. */
@Repository
@RequiredArgsConstructor
public class JpaWorkflowRepository implements WorkflowRepository {

    private final WorkflowJpaRepository jpa;

    @Override
    public Optional<Workflow> findById(UUID id) {
        return jpa.findById(id).map(JpaWorkflowRepository::toDomain);
    }

    @Override
    public void save(Workflow workflow) {
        WorkflowEntity entity = jpa.findById(workflow.id())
                .orElseGet(() -> new WorkflowEntity(workflow.id(), workflow.ownerId(), workflow.createdAt()));
        entity.setName(workflow.name());
        entity.setDescription(workflow.description());
        entity.setStatus(workflow.status().name());
        entity.setDraftDefinition(workflow.draft().document());
        entity.setRevision(workflow.revision());
        entity.setLatestVersion(workflow.latestVersion());
        entity.setPublishedRevision(workflow.publishedRevision());
        entity.setAclRevision(workflow.aclRevision());
        entity.setUpdatedAt(workflow.updatedAt());
        entity.setArchivedAt(workflow.archivedAt());
        entity.getPermissions().clear();
        entity.getPermissions()
                .addAll(workflow.permissions().stream()
                        .map(p -> new PermissionEmbeddable(p.userId(), p.role().name(), p.grantedBy(), p.grantedAt()))
                        .collect(Collectors.toSet()));
        jpa.save(entity);
    }

    @Override
    public PageResult<Workflow> search(WorkflowSearchCriteria criteria, Actor actor) {
        Sort sort = Sort.by(criteria.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, criteria.sortField())
                .and(Sort.by("id"));
        Page<WorkflowEntity> page = jpa.findAll(
                WorkflowSpecifications.visibleTo(actor)
                        .and(WorkflowSpecifications.hasStatus(criteria.status()))
                        .and(WorkflowSpecifications.nameContains(criteria.search())),
                PageRequest.of(criteria.page(), criteria.size(), sort));
        return new PageResult<>(
                page.getContent().stream().map(JpaWorkflowRepository::toDomain).toList(),
                page.getTotalElements(),
                criteria.page(),
                criteria.size());
    }

    @Override
    public long countVisible(Actor actor, boolean activeOnly) {
        return jpa.count(WorkflowSpecifications.visibleTo(actor)
                .and(WorkflowSpecifications.hasStatus(activeOnly ? WorkflowStatus.ACTIVE : null)));
    }

    static Workflow toDomain(WorkflowEntity e) {
        return Workflow.restore(
                e.getId(),
                e.getOwnerId(),
                e.getName(),
                e.getDescription(),
                WorkflowStatus.valueOf(e.getStatus()),
                WorkflowDefinition.of(e.getDraftDefinition()),
                e.getRevision(),
                e.getLatestVersion(),
                e.getPublishedRevision(),
                e.getAclRevision(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getArchivedAt(),
                e.getPermissions().stream()
                        .map(p -> new Permission(
                                p.getUserId(), WorkflowRole.valueOf(p.getRole()), p.getGrantedBy(), p.getGrantedAt()))
                        .toList());
    }
}
