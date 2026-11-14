package io.workflowengine.contracts.execution;

/** Failure classification reported by workers. See docs/events/catalog.md for codes. */
public record NodeError(String code, String message, boolean retryable) {}
