package com.orqentra.order.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final Limit BATCH = Limit.of(100);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, byte[]> outboxKafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = outboxKafkaTemplate;
    }

    /**
     * Publishes unpublished rows in id order, one at a time. The batch is deliberately not
     * parallelised: events for one order must reach Kafka in the order they were written.
     *
     * <p>A row is marked published only once the broker has acknowledged the send. That
     * makes delivery at-least-once, since a crash after the ack but before the update
     * republishes the event on the next run. That is precisely why consumers dedupe.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = repository.findByPublishedAtIsNullOrderByIdAsc(BATCH);

        for (OutboxEvent event : pending) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    event.getTopic(),
                    event.getMessageKey(),
                    event.getPayload().getBytes(StandardCharsets.UTF_8));

            // The payload is already JSON, so it goes out as raw bytes and the logical type
            // name is set by hand: the same header the Jackson deserializer reads back.
            record.headers().add("__TypeId__", event.getTypeName().getBytes(StandardCharsets.UTF_8));

            if (event.getRequestId() != null) {
                record.headers().add("X-Request-Id",
                        event.getRequestId().getBytes(StandardCharsets.UTF_8));
            }

            try {
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                // Leave it unpublished and stop, so the retry preserves id order.
                log.warn("Outbox publish failed for event {} on {}, will retry",
                        event.getEventId(), event.getTopic(), ex);
                return;
            }

            event.markPublished();
        }
    }
}
