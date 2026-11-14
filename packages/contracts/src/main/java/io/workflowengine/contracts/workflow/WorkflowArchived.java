package io.workflowengine.contracts.workflow;

import java.time.Instant;
import java.util.UUID;

public record WorkflowArchived(UUID workflowId, Instant archivedAt) {}
