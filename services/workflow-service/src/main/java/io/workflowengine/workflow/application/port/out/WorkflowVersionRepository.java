package io.workflowengine.workflow.application.port.out;

import io.workflowengine.workflow.domain.model.WorkflowVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowVersionRepository {

    void insert(WorkflowVersion version);

    Optional<WorkflowVersion> find(UUID workflowId, int version);

    List<WorkflowVersion> findAll(UUID workflowId);
}
