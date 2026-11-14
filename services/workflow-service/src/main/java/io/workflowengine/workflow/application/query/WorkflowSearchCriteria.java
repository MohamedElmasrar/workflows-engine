package io.workflowengine.workflow.application.query;

import io.workflowengine.workflow.domain.model.WorkflowStatus;

/**
 * @param search case-insensitive name fragment, optional
 * @param status optional status filter; archived workflows are excluded unless requested explicitly
 * @param sortField one of name, createdAt, updatedAt
 */
public record WorkflowSearchCriteria(
        String search, WorkflowStatus status, String sortField, boolean ascending, int page, int size) {

    public WorkflowSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (sortField == null || !(sortField.equals("name") || sortField.equals("createdAt") || sortField.equals("updatedAt"))) {
            sortField = "updatedAt";
        }
    }
}
