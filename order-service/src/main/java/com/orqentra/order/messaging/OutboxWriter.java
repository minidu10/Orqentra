package com.orqentra.order.messaging;

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
        repository.save(new OutboxEvent(eventId, topic, key, payload, typeName));
    }
}
