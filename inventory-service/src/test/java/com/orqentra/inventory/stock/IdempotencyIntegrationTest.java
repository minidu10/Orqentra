package com.orqentra.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.orqentra.inventory.events.OrderCreatedEvent;
import com.orqentra.inventory.events.StockReleaseRequestedEvent;
import com.orqentra.inventory.events.Topics;
import com.orqentra.inventory.messaging.OutboxEventRepository;
import com.orqentra.inventory.messaging.ProcessedEventId;
import com.orqentra.inventory.messaging.ProcessedEventRepository;
import com.orqentra.inventory.messaging.TestEventPublisher;
import com.orqentra.inventory.support.AbstractIntegrationTest;

/**
 * The property the whole processed_events design exists to provide: the same event,
 * delivered twice with the same eventId — which is exactly what Kafka's at-least-once
 * guarantee produces after a redelivery — has its business effect applied exactly once.
 *
 * <p>Release is the sharper case of the two. A duplicate deduction would at least be
 * caught by a stock check failing oddly; a duplicate release just silently hands back
 * stock that was never taken, and the inventory count would simply be wrong with no
 * error anywhere.
 */
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestEventPublisher publisher;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private int availableFor(String sku) {
        return inventoryRepository.findBySku(sku).map(InventoryItem::getAvailable).orElse(-1);
    }

    private long countOutboxRows(String topic, String key) {
        return outboxEventRepository.findAll().stream()
                .filter(row -> row.getTopic().equals(topic) && row.getMessageKey().equals(key))
                .count();
    }

    @Test
    void duplicateOrderCreated_deductsStockExactlyOnce() {
        String orderRef = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        int before = availableFor("SKU-001");

        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId, orderRef, "RESTAURANT-1", new BigDecimal("100.00"),
                List.of(new OrderCreatedEvent.Item("SKU-001", 2)));

        publisher.publish(Topics.ORDER_CREATED, orderRef, event);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(availableFor("SKU-001")).isEqualTo(before - 2));

        // The exact same eventId again — a genuine Kafka redelivery, not a new order.
        publisher.publish(Topics.ORDER_CREATED, orderRef, event);

        // Give the (correctly) skipped redelivery time to have done nothing, then assert
        // the stock level is unchanged and only one reservation was ever recorded.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(availableFor("SKU-001")).isEqualTo(before - 2);
                    assertThat(countOutboxRows(Topics.STOCK_RESERVED, orderRef)).isEqualTo(1);
                });

        assertThat(processedEventRepository.existsById(
                new ProcessedEventId(eventId, "inventory.order-created"))).isTrue();
    }

    @Test
    void duplicateStockReleaseRequested_restoresStockExactlyOnce() {
        // Reserve first so there is something real to release.
        String orderRef = UUID.randomUUID().toString();
        int before = availableFor("SKU-002");
        publisher.publish(Topics.ORDER_CREATED, orderRef, new OrderCreatedEvent(
                UUID.randomUUID().toString(), orderRef, "RESTAURANT-1", new BigDecimal("100.00"),
                List.of(new OrderCreatedEvent.Item("SKU-002", 4))));

        // Wait for the actual reservation to land before treating the stock level as a
        // baseline — reading it too early would just capture the pre-reservation value.
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(availableFor("SKU-002")).isEqualTo(before - 4));

        int afterReserve = availableFor("SKU-002");
        String releaseEventId = UUID.randomUUID().toString();
        StockReleaseRequestedEvent release = new StockReleaseRequestedEvent(
                releaseEventId, orderRef, List.of(new StockReleaseRequestedEvent.Item("SKU-002", 4)),
                "test");

        publisher.publish(Topics.STOCK_RELEASE_REQUESTED, orderRef, release);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(availableFor("SKU-002")).isEqualTo(afterReserve + 4));

        int afterFirstRelease = availableFor("SKU-002");

        // Redeliver the identical release event. A naive "restore(quantity)" call would
        // hand the stock back a second time and inflate it silently.
        publisher.publish(Topics.STOCK_RELEASE_REQUESTED, orderRef, release);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(availableFor("SKU-002")).isEqualTo(afterFirstRelease));

        assertThat(processedEventRepository.existsById(
                new ProcessedEventId(releaseEventId, "inventory.stock-release-requested"))).isTrue();
    }
}
