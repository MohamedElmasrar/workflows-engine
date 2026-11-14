package io.workflowengine.workflow.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface WorkflowJpaRepository extends JpaRepository<WorkflowEntity, UUID>, JpaSpecificationExecutor<WorkflowEntity> {}

interface WorkflowVersionJpaRepository extends JpaRepository<WorkflowVersionEntity, WorkflowVersionEntity.Key> {
    List<WorkflowVersionEntity> findByWorkflowIdOrderByVersionDesc(UUID workflowId);
}

interface AppUserJpaRepository extends JpaRepository<AppUserEntity, String> {
    Optional<AppUserEntity> findByUsernameIgnoreCase(String username);

    List<AppUserEntity> findByIdIn(Collection<String> ids);
}
