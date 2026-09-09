package com.orqentra.inventory.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.inventory.events.OrderCreatedEvent;
import com.orqentra.inventory.events.StockReleaseRequestedEvent;
import com.orqentra.inventory.events.Topics;
import com.orqentra.inventory.messaging.ProcessedEvents;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private static final String RELEASE_CONSUMER = "inventory.stock-release-requested";

    private final ReservationProcessor reservations;
    private final InventoryService inventory;
    private final ProcessedEvents processedEvents;

    public InventoryEventListener(ReservationProcessor reservations,
                                  InventoryService inventory,
                                  ProcessedEvents processedEvents) {
        this.reservations = reservations;
        this.inventory = inventory;
        this.processedEvents = processedEvents;
    }

    /**
     * Deliberately not transactional itself. The reservation and the rejection each need
     * their own transaction: a shortage must roll the deduction back, and the rejection
     * must survive that rollback. Wrapping both in one transaction here would let the
     * failed reservation poison the rejection, which is a failure that leaves no trace in
     * any log — the order simply never moves.
     */
    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            reservations.reserve(event);
        } catch (InsufficientStockException ex) {
            // A shortage is a normal business outcome and travels back as an event.
            // Nothing broader is caught: a database fault must propagate so the error
            // handler can retry and, if it never succeeds, route the record to the DLQ.
            reservations.reject(event, ex.getMessage());
        }
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
