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
import com.orqentra.inventory.messaging.TestEventPublisher;
import com.orqentra.inventory.support.AbstractIntegrationTest;

/**
 * Drives the real listener with a real broker and a real database: publishes to
 * order.created / stock.release.requested exactly as the order service would, and checks
 * the effect on the actual inventory row plus the outbox row the listener writes.
 *
 * <p>Whether that outbox row is actually delivered to Kafka is the OutboxPublisher's job,
 * proven separately in OutboxDeliveryTest — asserting on the row here keeps this test
 * about the listener's own behaviour, not the poller's timing.
 */
class InventoryEventListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestEventPublisher publisher;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private OrderCreatedEvent orderCreated(String orderRef, String sku, int quantity) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(), orderRef, "RESTAURANT-1",
                new BigDecimal("100.00"),
                List.of(new OrderCreatedEvent.Item(sku, quantity)));
    }

    private int availableFor(String sku) {
        return inventoryRepository.findBySku(sku).map(InventoryItem::getAvailable).orElse(-1);
    }

    private boolean outboxHas(String topic, String key) {
        return outboxEventRepository.findAll().stream()
                .anyMatch(row -> row.getTopic().equals(topic) && row.getMessageKey().equals(key));
    }

    @Test
    void orderCreated_withSufficientStock_deductsAndPublishesReservation() {
        String orderRef = UUID.randomUUID().toString();
        int before = availableFor("SKU-001");

        publisher.publish(Topics.ORDER_CREATED, orderRef, orderCreated(orderRef, "SKU-001", 2));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(availableFor("SKU-001")).isEqualTo(before - 2));

        assertThat(outboxHas(Topics.STOCK_RESERVED, orderRef)).isTrue();
        assertThat(outboxHas(Topics.STOCK_REJECTED, orderRef)).isFalse();
    }

    @Test
    void orderCreated_withInsufficientStock_rejectsAndLeavesStockUntouched() {
        String orderRef = UUID.randomUUID().toString();
        int before = availableFor("SKU-003"); // seeded low, at 8

        publisher.publish(Topics.ORDER_CREATED, orderRef, orderCreated(orderRef, "SKU-003", 999));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(outboxHas(Topics.STOCK_REJECTED, orderRef)).isTrue());

        assertThat(outboxHas(Topics.STOCK_RESERVED, orderRef)).isFalse();
        assertThat(availableFor("SKU-003")).isEqualTo(before);
    }

    @Test
    void stockReleaseRequested_restoresTheDeductedQuantity() {
        String orderRef = UUID.randomUUID().toString();
        int before = availableFor("SKU-002");

        publisher.publish(Topics.ORDER_CREATED, orderRef, orderCreated(orderRef, "SKU-002", 3));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(availableFor("SKU-002")).isEqualTo(before - 3));

        String releaseId = UUID.randomUUID().toString();
        publisher.publish(Topics.STOCK_RELEASE_REQUESTED, orderRef, new StockReleaseRequestedEvent(
                releaseId, orderRef, List.of(new StockReleaseRequestedEvent.Item("SKU-002", 3)),
                "Amount exceeds card limit"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(availableFor("SKU-002")).isEqualTo(before));
    }
}
