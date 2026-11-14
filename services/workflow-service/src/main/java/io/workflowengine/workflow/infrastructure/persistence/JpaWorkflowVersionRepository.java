package io.workflowengine.workflow.infrastructure.persistence;

import io.workflowengine.workflow.application.port.out.WorkflowVersionRepository;
import io.workflowengine.workflow.domain.model.WorkflowDefinition;
import io.workflowengine.workflow.domain.model.WorkflowVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkflowVersionRepository implements WorkflowVersionRepository {

    private final WorkflowVersionJpaRepository jpa;

    public JpaWorkflowVersionRepository(WorkflowVersionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(WorkflowVersion v) {
        jpa.save(new WorkflowVersionEntity(
                v.workflowId(),
                v.version(),
                v.name(),
                v.definition().document(),
                WorkflowDefinition.SCHEMA_VERSION,
                v.publishedBy(),
                v.publishedAt()));
    }

    @Override
    public Optional<WorkflowVersion> find(UUID workflowId, int version) {
        return jpa.findById(new WorkflowVersionEntity.Key(workflowId, version)).map(JpaWorkflowVersionRepository::toDomain);
    }

    @Override
    public List<WorkflowVersion> findAll(UUID workflowId) {
        return jpa.findByWorkflowIdOrderByVersionDesc(workflowId).stream()
                .map(JpaWorkflowVersionRepository::toDomain)
                .toList();
    }

    private static WorkflowVersion toDomain(WorkflowVersionEntity e) {
        return new WorkflowVersion(
                e.getWorkflowId(),
                e.getVersion(),
                e.getName(),
                WorkflowDefinition.of(e.getDefinition()),
                e.getPublishedBy(),
                e.getPublishedAt());
    }
}
