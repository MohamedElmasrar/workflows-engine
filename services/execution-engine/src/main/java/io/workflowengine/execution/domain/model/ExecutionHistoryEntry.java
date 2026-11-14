package io.workflowengine.execution.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of one state transition. Used for auditing and for replaying missed live updates (SSE). Never
 * contains node inputs or outputs, so no user data or secrets end up in the history stream.
 */
public record ExecutionHistoryEntry(
        long sequence,
        String type,
        String nodeId,
        UUID attemptId,
        String status,
        JsonNode details,
        Instant occurredAt) {

    public static final String EXECUTION_CREATED = "EXECUTION_CREATED";
    public static final String EXECUTION_STATUS = "EXECUTION_STATUS_CHANGED";
    public static final String NODE_STATUS = "NODE_STATUS_CHANGED";
    public static final String NODE_RETRY_SCHEDULED = "NODE_RETRY_SCHEDULED";
    public static final String STALE_RESULT_IGNORED = "STALE_RESULT_IGNORED";
}
