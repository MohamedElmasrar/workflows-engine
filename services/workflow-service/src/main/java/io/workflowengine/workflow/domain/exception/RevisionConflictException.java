package io.workflowengine.workflow.domain.exception;

public class RevisionConflictException extends DomainException {

    private final long currentRevision;

    public RevisionConflictException(long expected, long current) {
        super("The workflow was modified concurrently (expected revision " + expected + ", current " + current + ")");
        this.currentRevision = current;
    }

    public long currentRevision() {
        return currentRevision;
    }
}
