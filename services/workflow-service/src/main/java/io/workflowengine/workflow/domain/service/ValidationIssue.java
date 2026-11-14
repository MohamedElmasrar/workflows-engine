package io.workflowengine.workflow.domain.service;

/** A single validation problem. {@code nodeId}/{@code edgeId} point the editor at the offending element. */
public record ValidationIssue(String code, String message, String nodeId, String edgeId) {

    public static ValidationIssue general(String code, String message) {
        return new ValidationIssue(code, message, null, null);
    }

    public static ValidationIssue node(String code, String nodeId, String message) {
        return new ValidationIssue(code, message, nodeId, null);
    }

    public static ValidationIssue edge(String code, String edgeId, String message) {
        return new ValidationIssue(code, message, null, edgeId);
    }
}
