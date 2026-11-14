package io.workflowengine.worker.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Outcome of a handler invocation. */
public sealed interface NodeResult {

    record Success(JsonNode output, String branch) implements NodeResult {}

    record Failure(String code, String message, boolean retryable) implements NodeResult {}

    static NodeResult success(JsonNode output) {
        return new Success(output, null);
    }

    static NodeResult branch(JsonNode output, boolean selected) {
        return new Success(output, Boolean.toString(selected));
    }
}
