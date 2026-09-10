package com.orqentra.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;

import com.orqentra.order.support.AbstractIntegrationTest;

/**
 * Rows are written straight to the outbox table, bypassing OutboxWriter entirely, and
 * the real {@code @Scheduled} poller — already running in this context — is left to pick
 * them up on its own schedule. This proves the poller's own contract: every unpublished
 * row eventually gets marked published, and the wire order matches id order, which is
 * the property the saga depends on to keep one order's events in sequence.
 */
class OutboxDeliveryTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private KafkaConnectionDetails connectionDetails;

    @Test
    void everyRow_isPublishedAndMarked_inIdOrder() throws Exception {
        String topic = "test.outbox.delivery." + UUID.randomUUID();
        createTopic(topic);

        String key = "order-" + UUID.randomUUID(); // arbitrary Kafka key, not stored in any VARCHAR(36) column
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            OutboxEvent row = outboxEventRepository.save(new OutboxEvent(
                    UUID.randomUUID().toString(), topic, key, "payload-" + i, "test.event", null, null));
            ids.add(row.getId());
        }
        // The repository assigns ids in insertion order (BIGSERIAL), so this is also the
        // order the poller's own findByPublishedAtIsNullOrderByIdAsc query must return.
        assertThat(ids).isSorted();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<OutboxEvent> rows = outboxEventRepository.findAllById(ids);
            assertThat(rows).allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
        });

        List<String> deliveredPayloads = consumeAll(topic, 5, Duration.ofSeconds(10));
        assertThat(deliveredPayloads).containsExactly(
                "payload-0", "payload-1", "payload-2", "payload-3", "payload-4");
    }

    private void createTopic(String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(java.util.Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }
    }

    private List<String> consumeAll(String topic, int expectedCount, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<String> values = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));

            while (values.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, String> record : records) {
                    values.add(record.value());
                }
            }
        }
        return values;
    }

    private String bootstrapServers() {
        return String.join(",", connectionDetails.getBootstrapServers());
    }
}
