package com.orqentra.inventory.messaging;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes an event to the outbox table instead of sending it to Kafka. The row joins the
 * caller transaction, so the state change and the intent to publish commit together, and
 * the window where a service could save state and then die before publishing is gone.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxWriter(OutboxEventRepository repository,
                        ObjectMapper objectMapper,
                        Tracer tracer,
                        Propagator propagator) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public void append(String topic, String key, String eventId, Object event) {
        String typeName = EventTypes.logicalNameFor(event.getClass());
        String payload = objectMapper.writeValueAsString(event);
        // Stored with the event so the correlation id survives the hop into Kafka
        // and can be restored by whichever service consumes it.
        String requestId = MDC.get("requestId");
        repository.save(new OutboxEvent(
                eventId, topic, key, payload, typeName, requestId, currentTraceparent()));
    }

    /**
     * The W3C traceparent of the span that is writing this row.
     *
     * <p>Captured here rather than at publish time on purpose. The poller runs later, on a
     * scheduler thread, in a trace of its own; without this the published event would
     * begin a brand new trace and the saga would appear in Jaeger as a handful of
     * unconnected fragments instead of one story.
     */
    private String currentTraceparent() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get("traceparent");
    }
}
