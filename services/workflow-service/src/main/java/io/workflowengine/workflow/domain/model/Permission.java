package io.workflowengine.workflow.domain.model;

import java.time.Instant;

public record Permission(String userId, WorkflowRole role, String grantedBy, Instant grantedAt) {}
