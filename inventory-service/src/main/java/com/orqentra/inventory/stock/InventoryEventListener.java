package com.orqentra.inventory.stock;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.inventory.events.OrderCreatedEvent;
import com.orqentra.inventory.events.StockRejectedEvent;
import com.orqentra.inventory.events.StockReleaseRequestedEvent;
import com.orqentra.inventory.events.StockReservedEvent;
import com.orqentra.inventory.events.Topics;
import com.orqentra.inventory.messaging.OutboxWriter;
import com.orqentra.inventory.messaging.ProcessedEvents;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private static final String ORDER_CREATED_CONSUMER = "inventory.order-created";
    private static final String RELEASE_CONSUMER = "inventory.stock-release-requested";

    private final InventoryService inventory;
    private final OutboxWriter outbox;
    private final ProcessedEvents processedEvents;

    public InventoryEventListener(InventoryService inventory,
                                  OutboxWriter outbox,
                                  ProcessedEvents processedEvents) {
        this.inventory = inventory;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
    }

    /**
     * One transaction covers the dedupe claim, the stock deduction and the outbox row, so
     * a redelivery cannot deduct twice and a crash cannot deduct without producing an event.
     */
    @KafkaListener(topics = Topics.ORDER_CREATED)
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        if (!processedEvents.claim(event.eventId(), ORDER_CREATED_CONSUMER)) {
            log.info("Skipping already processed order.created event {}", event.eventId());
            return;
        }

        log.info("Order created {}, reserving {} line(s)",
                event.orderReference(), event.items().size());

        List<DeductRequest.Line> lines = event.items().stream()
                .map(item -> new DeductRequest.Line(item.sku(), item.quantity()))
                .toList();

        try {
            inventory.deduct(lines);
        } catch (InsufficientStockException ex) {
            // A shortage is a normal business outcome, so it travels back as an event.
            // Nothing broader is caught here: a database fault must propagate so the error
            // handler can retry and, if it never succeeds, route the record to the DLQ.
            log.info("Rejecting order {}: {}", event.orderReference(), ex.getMessage());
            String rejectedId = UUID.randomUUID().toString();
            outbox.append(Topics.STOCK_REJECTED, event.orderReference(), rejectedId,
                    new StockRejectedEvent(rejectedId, event.orderReference(), ex.getMessage()));
            return;
        }

        log.info("Reserved stock for order {}", event.orderReference());
        String reservedId = UUID.randomUUID().toString();
        outbox.append(Topics.STOCK_RESERVED, event.orderReference(), reservedId,
                new StockReservedEvent(reservedId, event.orderReference(), event.amount()));
    }

    /**
     * The compensating action. The dedupe claim is what makes a redelivery safe here: the
     * release is not naturally idempotent, so without it a second delivery would restore
     * the stock twice and quietly inflate the inventory.
     */
    @KafkaListener(topics = Topics.STOCK_RELEASE_REQUESTED)
    @Transactional
    public void onStockReleaseRequested(StockReleaseRequestedEvent event) {
        if (!processedEvents.claim(event.eventId(), RELEASE_CONSUMER)) {
            log.info("Skipping already processed stock.release.requested event {} for order {}",
                    event.eventId(), event.orderReference());
            return;
        }

        log.info("Releasing stock for order {}: {}", event.orderReference(), event.reason());

        List<DeductRequest.Line> lines = event.items().stream()
                .map(item -> new DeductRequest.Line(item.sku(), item.quantity()))
                .toList();

        inventory.release(lines);
    }
}
