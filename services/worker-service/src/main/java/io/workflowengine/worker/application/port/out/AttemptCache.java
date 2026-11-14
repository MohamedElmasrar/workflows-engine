package io.workflowengine.worker.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived record of attempts this worker pool has finished. A redelivered task whose attempt already finished is
 * answered from the cache instead of re-running side effects.
 */
public interface AttemptCache {

    record CachedResult(String eventType, String payloadJson) {}

    Optional<CachedResult> finished(UUID attemptId);

    void storeFinished(UUID attemptId, CachedResult result);
}
