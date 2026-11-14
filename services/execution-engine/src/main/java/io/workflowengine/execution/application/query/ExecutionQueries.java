package io.workflowengine.execution.application.query;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-side views (CQRS-lite: served by SQL queries, not by loading aggregates). */
public final class ExecutionQueries {

    private ExecutionQueries() {}

    public record ExecutionFilter(UUID workflowId, String status, Instant from, Instant to, int page, int size) {
        public ExecutionFilter {
            if (page < 0 || size < 1 || size > 100) {
                throw new IllegalArgumentException("page must be >= 0 and size between 1 and 100");
            }
        }
    }

    public record ExecutionSummary(
            UUID id,
            UUID workflowId,
            String workflowName,
            int workflowVersion,
            String status,
            String triggerType,
            String triggeredBy,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorMessage,
            UUID retryOf) {}

    public record HistoryEntryView(
            long sequence,
            String type,
            String nodeId,
            UUID attemptId,
            String status,
            JsonNode details,
            Instant occurredAt) {}

    public record Stats(long executionsToday, long completedToday, long failedToday, long running, long pending) {}

    public record Page<T>(List<T> items, long totalItems, int page, int size) {}
}
