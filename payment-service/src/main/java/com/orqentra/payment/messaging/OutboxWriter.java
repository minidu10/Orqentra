package com.orqentra.payment.messaging;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

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

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(String topic, String key, String eventId, Object event) {
        String typeName = EventTypes.logicalNameFor(event.getClass());
        String payload = objectMapper.writeValueAsString(event);
        // Stored with the event so the correlation id survives the hop into Kafka
        // and can be restored by whichever service consumes it.
        String requestId = MDC.get("requestId");
        repository.save(new OutboxEvent(eventId, topic, key, payload, typeName, requestId));
    }
}
