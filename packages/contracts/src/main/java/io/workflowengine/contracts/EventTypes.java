package io.workflowengine.contracts;

/** Logical event type names carried in {@link EventEnvelope#eventType()}. */
public final class EventTypes {

    public static final String WORKFLOW_ACCESS_UPDATED = "WorkflowAccessUpdated";
    public static final String WORKFLOW_VERSION_PUBLISHED = "WorkflowVersionPublished";
    public static final String WORKFLOW_ARCHIVED = "WorkflowArchived";

    public static final String WORKFLOW_EXECUTION_REQUESTED = "WorkflowExecutionRequested";
    public static final String WORKFLOW_EXECUTION_STARTED = "WorkflowExecutionStarted";
    public static final String WORKFLOW_EXECUTION_COMPLETED = "WorkflowExecutionCompleted";
    public static final String WORKFLOW_EXECUTION_FAILED = "WorkflowExecutionFailed";
    public static final String WORKFLOW_EXECUTION_CANCELLED = "WorkflowExecutionCancelled";
    public static final String WORKFLOW_EXECUTION_TIMED_OUT = "WorkflowExecutionTimedOut";

    public static final String NODE_EXECUTION_SCHEDULED = "NodeExecutionScheduled";
    public static final String NODE_EXECUTION_STARTED = "NodeExecutionStarted";
    public static final String NODE_EXECUTION_COMPLETED = "NodeExecutionCompleted";
    public static final String NODE_EXECUTION_FAILED = "NodeExecutionFailed";

    private EventTypes() {}
}
