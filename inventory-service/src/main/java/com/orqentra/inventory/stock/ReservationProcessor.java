package com.orqentra.inventory.stock;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.inventory.events.OrderCreatedEvent;
import com.orqentra.inventory.events.StockRejectedEvent;
import com.orqentra.inventory.events.StockReservedEvent;
import com.orqentra.inventory.events.Topics;
import com.orqentra.inventory.messaging.OutboxWriter;
import com.orqentra.inventory.messaging.ProcessedEvents;

/**
 * Reserving stock and rejecting an order are two separate transactions, on purpose.
 *
 * <p>A shortage has to roll the deduction back — a multi-line order that fails on its
 * third line must not keep the first two — and {@code deduct} throws to make that happen.
 * But an exception thrown out of a participating {@code @Transactional} method marks the
 * whole surrounding transaction rollback-only. Writing the rejection inside that same
 * transaction therefore looked correct and silently did nothing: the outbox row never
 * committed, no {@code stock.rejected} was ever published, and the order sat at PENDING
 * until the record gave up and went to the dead letter queue.
 *
 * <p>Keeping them apart is what makes both guarantees hold: the stock change rolls back,
 * and the rejection is still recorded, each with its own deduplication marker.
 */
@Component
public class ReservationProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReservationProcessor.class);

    private static final String ORDER_CREATED_CONSUMER = "inventory.order-created";

    private final InventoryService inventory;
    private final OutboxWriter outbox;
    private final ProcessedEvents processedEvents;

    public ReservationProcessor(InventoryService inventory,
                                OutboxWriter outbox,
                                ProcessedEvents processedEvents) {
        this.inventory = inventory;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
    }

    /**
     * Claims the event, deducts the stock and records the reservation, all or nothing.
     * Throws when there is not enough stock, which rolls every part of it back.
     *
     * @return false when the event was already handled
     */
    @Transactional
    public boolean reserve(OrderCreatedEvent event) {
        if (!processedEvents.claim(event.eventId(), ORDER_CREATED_CONSUMER)) {
            log.info("Skipping already processed order.created event {}", event.eventId());
            return false;
        }

        log.info("Order created {}, reserving {} line(s)",
                event.orderReference(), event.items().size());

        List<DeductRequest.Line> lines = event.items().stream()
                .map(item -> new DeductRequest.Line(item.sku(), item.quantity()))
                .toList();

        inventory.deduct(lines);

        log.info("Reserved stock for order {}", event.orderReference());
        String reservedId = UUID.randomUUID().toString();
        outbox.append(Topics.STOCK_RESERVED, event.orderReference(), reservedId,
                new StockReservedEvent(reservedId, event.orderReference(), event.amount()));
        return true;
    }

    /**
     * Records the rejection in a transaction of its own, since the reservation attempt
     * has already rolled back and taken its deduplication marker with it.
     */
    @Transactional
    public void reject(OrderCreatedEvent event, String reason) {
        if (!processedEvents.claim(event.eventId(), ORDER_CREATED_CONSUMER)) {
            log.info("Rejection for event {} already recorded", event.eventId());
            return;
        }

        log.info("Rejecting order {}: {}", event.orderReference(), reason);
        String rejectedId = UUID.randomUUID().toString();
        outbox.append(Topics.STOCK_REJECTED, event.orderReference(), rejectedId,
                new StockRejectedEvent(rejectedId, event.orderReference(), reason));
    }
}
