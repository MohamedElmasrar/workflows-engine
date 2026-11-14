package io.workflowengine.execution.domain.model;

/** Directed edge. {@code sourceHandle} selects a branch output of a CONDITION node; null means unconditional. */
public record GraphEdge(String id, String source, String target, String sourceHandle) {

    public boolean isActiveFor(String selectedBranch) {
        return sourceHandle == null || sourceHandle.equals(selectedBranch);
    }
}
