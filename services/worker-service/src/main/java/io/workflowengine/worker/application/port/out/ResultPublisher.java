package io.workflowengine.worker.application.port.out;

/** Publishes node results synchronously: returns only after the broker acknowledged the record. */
public interface ResultPublisher {

    void publish(String eventType, String executionId, Object payload);
}
