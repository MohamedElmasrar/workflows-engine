package io.workflowengine.workflow.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.workflow.domain.service.ValidationResult;

/** Structural validation against the versioned workflow-definition JSON schema. */
public interface DefinitionSchemaValidator {

    ValidationResult validate(JsonNode document);
}
