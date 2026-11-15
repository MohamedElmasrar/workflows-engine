package io.workflowengine.workflow.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.workflow.application.port.out.DefinitionSchemaValidator;
import io.workflowengine.workflow.domain.model.WorkflowDefinition;
import io.workflowengine.workflow.domain.service.GraphValidator;
import io.workflowengine.workflow.domain.service.ValidationResult;
import lombok.RequiredArgsConstructor;

/** Two-stage validation: JSON schema (structure) first, graph rules only if the structure is sound. */
@RequiredArgsConstructor
public class WorkflowValidation {

    private final DefinitionSchemaValidator schemaValidator;
    private final GraphValidator graphValidator;

    public ValidationResult validate(JsonNode document) {
        ValidationResult structural = schemaValidator.validate(document);
        if (!structural.valid()) {
            return structural;
        }
        return graphValidator.validate(WorkflowDefinition.of(document));
    }
}
