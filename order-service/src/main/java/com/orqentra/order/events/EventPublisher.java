package com.orqentra.order.events;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * The key is always the order reference. The key picks the partition and Kafka only
     * guarantees ordering within a partition, so this keeps one order's events in
     * sequence while leaving different orders free to be processed in parallel.
     */
    public void publish(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
    }
}
