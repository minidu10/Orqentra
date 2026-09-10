package com.orqentra.order.messaging;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Publishes an event exactly the way {@code OutboxPublisher} does: raw JSON bytes with a
 * {@code __TypeId__} header, so a test can simulate the other services' publishes without
 * needing them running.
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
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add("__TypeId__",
                EventTypes.logicalNameFor(event.getClass()).getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);
    }
}
