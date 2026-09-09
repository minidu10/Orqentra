package com.orqentra.inventory.stock;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.orqentra.inventory.events.EventPublisher;
import com.orqentra.inventory.events.OrderCreatedEvent;
import com.orqentra.inventory.events.StockRejectedEvent;
import com.orqentra.inventory.events.StockReleaseRequestedEvent;
import com.orqentra.inventory.events.StockReservedEvent;
import com.orqentra.inventory.events.Topics;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventory;
    private final EventPublisher events;

    public InventoryEventListener(InventoryService inventory, EventPublisher events) {
        this.inventory = inventory;
        this.events = events;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Order created {}, reserving {} line(s)",
                event.orderReference(), event.items().size());

        List<DeductRequest.Line> lines = event.items().stream()
                .map(item -> new DeductRequest.Line(item.sku(), item.quantity()))
                .toList();

        try {
            inventory.deduct(lines);
        } catch (InsufficientStockException ex) {
            // A shortage is a normal business outcome, so it travels back as an event.
            // Nothing broader is caught here: a database fault must propagate so Kafka
            // redelivers the message instead of silently rejecting the order.
            log.info("Rejecting order {}: {}", event.orderReference(), ex.getMessage());
            events.publish(Topics.STOCK_REJECTED, event.orderReference(),
                    new StockRejectedEvent(UUID.randomUUID().toString(),
                            event.orderReference(), ex.getMessage()));
            return;
        }

        log.info("Reserved stock for order {}", event.orderReference());
        events.publish(Topics.STOCK_RESERVED, event.orderReference(),
                new StockReservedEvent(UUID.randomUUID().toString(),
                        event.orderReference(), event.amount()));
    }

    /**
     * The compensating action. Deliberately has no deduplication check: a redelivery here
     * restores the stock twice, which is wrong and must stay observable until Phase 5.
     */
    @KafkaListener(topics = Topics.STOCK_RELEASE_REQUESTED)
    public void onStockReleaseRequested(StockReleaseRequestedEvent event) {
        log.info("Releasing stock for order {}: {}", event.orderReference(), event.reason());

        List<DeductRequest.Line> lines = event.items().stream()
                .map(item -> new DeductRequest.Line(item.sku(), item.quantity()))
                .toList();

        inventory.release(lines);
    }
}
