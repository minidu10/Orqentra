package com.orqentra.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;

import com.orqentra.inventory.events.OrderCreatedEvent;
import com.orqentra.inventory.events.Topics;
import com.orqentra.inventory.stock.InventoryRepository;
import com.orqentra.inventory.support.AbstractIntegrationTest;

import tools.jackson.databind.ObjectMapper;

/**
 * The property the retry/DLQ configuration exists to provide: a message that can never
 * succeed must not block the partition, but must also not skip the retries a message
 * that legitimately might succeed on a later attempt deserves.
 */
class DeadLetterRoutingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestEventPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    // @Value("${spring.kafka.bootstrap-servers}") would silently read the plain
    // property and ignore the running Testcontainers broker: KafkaProperties has no
    // overload that consults KafkaConnectionDetails, and @ServiceConnection never
    // writes the property itself, only this bean. See MessagingConfig for the same
    // fix applied to the production outbox producer.
    @Autowired
    private KafkaConnectionDetails connectionDetails;

    @Test
    void malformedPayload_goesStraightToDlq_withoutBlockingLaterValidMessages() throws Exception {
        String dlqTopic = Topics.ORDER_CREATED + ".dlq";
        ensureTopicExists(dlqTopic);

        // A freshly created consumer group's first-ever partition assignment can itself
        // take several seconds — a one-time cold-start cost that has nothing to do with
        // retry behaviour. Publishing and waiting on an ordinary valid message first
        // forces that rebalance to finish, so the timing assertions below measure only
        // the thing they are meant to: retry backoff, not group formation.
        int beforeWarmup = availableFor("SKU-001");
        String warmupRef = "warmup-" + UUID.randomUUID();
        publisher.publish(Topics.ORDER_CREATED, warmupRef, new OrderCreatedEvent(
                UUID.randomUUID().toString(), warmupRef, "RESTAURANT-1",
                new java.math.BigDecimal("10.00"),
                List.of(new OrderCreatedEvent.Item("SKU-001", 1))));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(availableFor("SKU-001")).isEqualTo(beforeWarmup - 1));

        // 1) Cannot be deserialised at all: not JSON. The ErrorHandlingDeserializer turns
        // this into a DeserializationException, which is configured as non-retryable, so
        // it must reach the DLQ without going through the backoff.
        String badKey = "bad-json-" + UUID.randomUUID();
        publisher.publishRaw(Topics.ORDER_CREATED, badKey,
                "{not valid json".getBytes(StandardCharsets.UTF_8), "orderCreated");

        long startedAt = System.currentTimeMillis();
        String deserFailureKey = awaitDlqMessage(dlqTopic, badKey, Duration.ofSeconds(15));
        long elapsedMs = System.currentTimeMillis() - startedAt;
        assertThat(deserFailureKey).isEqualTo(badKey);
        // The backoff starts at 1s and doubles for 3 attempts (1s + 2s = 3s minimum if it
        // retried). The consumer group is already warm at this point, so a message that
        // skipped retries lands comfortably under that.
        assertThat(elapsedMs).isLessThan(2500);

        // 2) Deserialises fine, but the listener itself throws (null items -> NPE inside
        // ReservationProcessor.reserve). This is retryable, so it must be retried first.
        String npeKey = "npe-" + UUID.randomUUID();
        byte[] malformedButValidJson = objectMapper.writeValueAsBytes(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "orderReference", npeKey,
                "restaurantId", "RESTAURANT-1",
                "amount", 10));
        // items is deliberately absent -> null after deserialisation.
        long retryStart = System.currentTimeMillis();
        publisher.publishRaw(Topics.ORDER_CREATED, npeKey, malformedButValidJson, "orderCreated");

        String landedKey = awaitDlqMessage(dlqTopic, npeKey, Duration.ofSeconds(20));
        long retryElapsedMs = System.currentTimeMillis() - retryStart;
        assertThat(landedKey).isEqualTo(npeKey);
        // Three attempts with 1s then 2s backoff: must take at least that long.
        assertThat(retryElapsedMs).isGreaterThanOrEqualTo(2900);

        // 3) A perfectly valid message published to the SAME topic, behind both poison
        // messages in the partition, must still be processed. This is the property the
        // whole DLQ mechanism exists for: one bad record must not wedge the others.
        int beforeFinal = availableFor("SKU-001");
        String goodOrderRef = "good-" + UUID.randomUUID();
        publisher.publish(Topics.ORDER_CREATED, goodOrderRef, new OrderCreatedEvent(
                UUID.randomUUID().toString(), goodOrderRef, "RESTAURANT-1",
                new java.math.BigDecimal("10.00"),
                List.of(new OrderCreatedEvent.Item("SKU-001", 1))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(availableFor("SKU-001")).isEqualTo(beforeFinal - 1));
        // Reaching here without timing out, with the exact expected decrement, proves the
        // partition kept moving rather than being wedged behind the two poison messages.
    }

    private int availableFor(String sku) {
        return inventoryRepository.findBySku(sku)
                .map(com.orqentra.inventory.stock.InventoryItem::getAvailable)
                .orElse(-1);
    }

    /**
     * Creates the DLQ topic ahead of time, with a single partition to match every other
     * topic in this project. Doing this upfront removes the auto-create race (the first
     * poison message would otherwise be what creates the topic) from the timing
     * measurements below, which only care about retry backoff.
     */
    private void ensureTopicExists(String topic) throws ExecutionException, InterruptedException {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
            if (!admin.listTopics().names().get().contains(topic)) {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
            }
        }
    }

    /**
     * Polls the dead letter topic from the beginning until a record with the given key
     * appears. Uses manual partition assignment rather than {@code subscribe()}: a
     * consumer group join (FindCoordinator/JoinGroup/SyncGroup) can itself take several
     * seconds on a fresh group, which would swamp the very backoff timing this test is
     * trying to measure. Manual assignment skips group coordination entirely.
     */
    private String awaitDlqMessage(String topic, String expectedKey, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));

            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, byte[]> record : records) {
                    if (expectedKey.equals(record.key())) {
                        return record.key();
                    }
                }
            }
        }
        throw new AssertionError("No DLQ message with key " + expectedKey + " arrived within " + timeout);
    }

    private String bootstrapServers() {
        return String.join(",", connectionDetails.getBootstrapServers());
    }
}
