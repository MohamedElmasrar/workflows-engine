package io.workflowengine.worker.infrastructure.cache;

import io.workflowengine.worker.application.port.out.AttemptCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed attempt cache shared by all worker replicas. Redis failures degrade to "not cached" (the task runs
 * again) rather than blocking task processing: dedupe here is an optimisation, correctness is ensured by the engine.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisAttemptCache implements AttemptCache {
    private static final String PREFIX = "worker:attempt:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    @Override
    public Optional<CachedResult> finished(UUID attemptId) {
        try {
            String value = redis.opsForValue().get(PREFIX + attemptId);
            if (value == null) {
                return Optional.empty();
            }
            int split = value.indexOf('\n');
            return Optional.of(new CachedResult(value.substring(0, split), value.substring(split + 1)));
        } catch (RuntimeException e) {
            log.warn("Attempt cache unavailable, treating {} as not finished: {}", attemptId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void storeFinished(UUID attemptId, CachedResult result) {
        try {
            redis.opsForValue().set(PREFIX + attemptId, result.eventType() + "\n" + result.payloadJson(), ttl);
        } catch (RuntimeException e) {
            log.warn("Could not cache result of attempt {}: {}", attemptId, e.getMessage());
        }
    }
}
