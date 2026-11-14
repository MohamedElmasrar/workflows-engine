package io.workflowengine.execution.domain.model;

/** How the aggregate treated a worker message. */
public enum ResultOutcome {
    /** State changed. */
    APPLIED,
    /** Same information as already recorded (e.g. a second Started for a running attempt). */
    DUPLICATE,
    /** Not for the current attempt, or the execution/node is no longer active: ignored (fencing). */
    STALE
}
