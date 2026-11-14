package io.workflowengine.contracts;

/** Kafka topic names. Convention: {@code <context>.<stream>.v<major>}; dead letters use the {@code .dlt} suffix. */
public final class Topics {

    public static final String WORKFLOW_EVENTS = "workflow.events.v1";
    public static final String EXECUTION_REQUESTS = "execution.requests.v1";
    public static final String NODE_TASKS = "execution.node-tasks.v1";
    public static final String NODE_RESULTS = "execution.node-results.v1";
    public static final String EXECUTION_LIFECYCLE = "execution.lifecycle.v1";
    public static final String DLT_SUFFIX = ".dlt";

    private Topics() {}

    public static String deadLetter(String topic) {
        return topic + DLT_SUFFIX;
    }
}
