package io.workflowengine.platform.messaging;

import io.workflowengine.contracts.EventEnvelope;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Correlation data for the current thread, stored in the SLF4J MDC so it also appears in every log line. Consumers
 * open a scope per message; the outbox reads the scope when new events are appended.
 */
public final class MessageContext {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CAUSATION_ID = "causationId";

    private MessageContext() {}

    public static Scope open(EventEnvelope envelope) {
        String correlationId = envelope.correlationId() != null
                ? envelope.correlationId()
                : envelope.eventId().toString();
        return open(correlationId, envelope.eventId().toString());
    }

    public static Scope open(String correlationId, String causationId) {
        String previousCorrelation = MDC.get(CORRELATION_ID);
        String previousCausation = MDC.get(CAUSATION_ID);
        MDC.put(CORRELATION_ID, correlationId);
        putOrRemove(CAUSATION_ID, causationId);
        return () -> {
            putOrRemove(CORRELATION_ID, previousCorrelation);
            putOrRemove(CAUSATION_ID, previousCausation);
        };
    }

    /** Correlation id of the current scope, or a new one if there is none. */
    public static String correlationId() {
        String current = MDC.get(CORRELATION_ID);
        return current != null ? current : UUID.randomUUID().toString();
    }

    public static String causationId() {
        return MDC.get(CAUSATION_ID);
    }

    private static void putOrRemove(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
