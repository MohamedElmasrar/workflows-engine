package io.workflowengine.workflow.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Immutable snapshot of a workflow definition. Never modified after creation (also enforced by a DB trigger). */
public record WorkflowVersion(
        UUID workflowId,
        int version,
        String name,
        WorkflowDefinition definition,
        String publishedBy,
        Instant publishedAt) {}
