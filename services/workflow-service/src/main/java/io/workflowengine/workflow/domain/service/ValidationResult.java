package io.workflowengine.workflow.domain.service;

import java.util.List;

public record ValidationResult(List<ValidationIssue> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
