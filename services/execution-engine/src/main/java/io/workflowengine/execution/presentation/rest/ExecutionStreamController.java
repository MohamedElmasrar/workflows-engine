package io.workflowengine.execution.presentation.rest;

import io.workflowengine.execution.application.query.ExecutionQueries.HistoryEntryView;
import io.workflowengine.execution.application.usecase.ExecutionQueryService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live execution updates over Server-Sent Events. Events come from the persisted history table, so a client that
 * reconnects with {@code Last-Event-ID} (or {@code ?after=}) receives exactly the transitions it missed, regardless of
 * which engine replica serves it. Polling the table keeps replicas stateless at the cost of up to one poll interval
 * of latency.
 */
@RestController
public class ExecutionStreamController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStreamController.class);

    private final ExecutionQueryService queries;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
    private final Duration pollInterval;

    public ExecutionStreamController(
            ExecutionQueryService queries, @Value("${engine.sse.poll-interval:500ms}") Duration pollInterval) {
        this.queries = queries;
        this.pollInterval = pollInterval;
    }

    @GetMapping(path = "/api/v1/executions/{id}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            Authentication auth,
            @PathVariable UUID id,
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
            @RequestParam(required = false) Long after) {
        List<HistoryEntryView> initial = queries.history(
                ExecutionController.actor(auth), id, lastEventId != null ? lastEventId : after == null ? 0 : after, 500);
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        AtomicLong cursor = new AtomicLong(lastEventId != null ? lastEventId : after == null ? 0 : after);
        AtomicLong idleTicks = new AtomicLong();
        AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();
        Runnable stop = () -> {
            ScheduledFuture<?> f = task.get();
            if (f != null) {
                f.cancel(false);
            }
        };
        emitter.onCompletion(stop);
        emitter.onTimeout(stop);
        emitter.onError(e -> stop.run());

        send(emitter, initial, cursor);
        task.set(scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        List<HistoryEntryView> events = queries.historySince(id, cursor.get());
                        if (!events.isEmpty()) {
                            idleTicks.set(0);
                            send(emitter, events, cursor);
                        } else if (queries.isTerminal(id)) {
                            emitter.send(SseEmitter.event().name("end").data("{}"));
                            emitter.complete();
                            stop.run();
                        } else if (idleTicks.incrementAndGet() * pollInterval.toMillis() >= 15_000) {
                            idleTicks.set(0);
                            emitter.send(SseEmitter.event().comment("keep-alive"));
                        }
                    } catch (IOException | IllegalStateException e) {
                        stop.run();
                    } catch (RuntimeException e) {
                        log.warn("SSE poll for execution {} failed: {}", id, e.getMessage());
                    }
                },
                pollInterval.toMillis(),
                pollInterval.toMillis(),
                TimeUnit.MILLISECONDS));
        return emitter;
    }

    private static void send(SseEmitter emitter, List<HistoryEntryView> events, AtomicLong cursor) {
        for (HistoryEntryView event : events) {
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.sequence()))
                        .name("transition")
                        .data(event, MediaType.APPLICATION_JSON));
                cursor.set(event.sequence());
            } catch (IOException e) {
                throw new IllegalStateException("client disconnected", e);
            }
        }
    }
}
