package com.orqentra.inventory.messaging;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Publishes an event exactly the way {@code OutboxPublisher} does: raw JSON bytes with a
 * {@code __TypeId__} header carrying the logical name. Tests that need to simulate a
 * message arriving from Kafka — including a redelivery, or another service's publish —
 * use this rather than going through the outbox, since the outbox's own behaviour is
 * covered by its own tests.
 */
@Component
public class TestEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TestEventPublisher(KafkaTemplate<String, byte[]> outboxKafkaTemplate,
                              ObjectMapper objectMapper) {
        this.kafkaTemplate = outboxKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object event) {
        byte[] payload = objectMapper.writeValueAsBytes(event);
        publishRaw(topic, key, payload, EventTypes.logicalNameFor(event.getClass()));
    }

    /** For deliberately malformed messages: caller supplies raw bytes and any type header. */
    public void publishRaw(String topic, String key, byte[] payload, String typeName) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);
        if (typeName != null) {
            record.headers().add("__TypeId__", typeName.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record);
    }
}
