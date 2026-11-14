package io.workflowengine.platform.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Captures the current trace context when an outbox row is written and restores it when the row is published, so a
 * trace continues across the asynchronous outbox hop. Without a tracer it does nothing.
 */
public class TraceContextCapture {

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContextCapture(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public static TraceContextCapture noop() {
        return new TraceContextCapture(null, null);
    }

    public Map<String, String> capture() {
        Map<String, String> headers = new HashMap<>();
        if (tracer == null || tracer.currentTraceContext().context() == null) {
            return headers;
        }
        propagator.inject(tracer.currentTraceContext().context(), headers, Map::put);
        return headers;
    }

    public <T> T runInContext(Map<String, String> headers, String spanName, Supplier<T> action) {
        if (tracer == null || headers.isEmpty()) {
            return action.get();
        }
        Span span = propagator.extract(headers, Map::get).name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return action.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
