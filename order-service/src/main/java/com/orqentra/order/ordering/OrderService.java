package com.orqentra.order.ordering;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.order.catalog.CatalogService;
import com.orqentra.order.events.OrderCreatedEvent;
import com.orqentra.order.events.StockReleaseRequestedEvent;
import com.orqentra.order.events.Topics;
import com.orqentra.order.messaging.OutboxWriter;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CatalogService catalog;
    private final OutboxWriter outbox;

    public OrderService(OrderRepository orders,
                        CatalogService catalog,
                        OutboxWriter outbox) {
        this.orders = orders;
        this.catalog = catalog;
        this.outbox = outbox;
    }

    @Transactional
    public OrderResponse place(PlaceOrderRequest request) {
        if (request.restaurantId() == null || request.restaurantId().isBlank()) {
            throw new IllegalArgumentException("restaurantId is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }

        Order order = new Order(UUID.randomUUID().toString(), request.restaurantId());
        List<OrderCreatedEvent.Item> eventItems = new ArrayList<>();

        for (PlaceOrderRequest.Line line : request.items()) {
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for " + line.sku());
            }

            // Price lookup stays ahead of the outbox write so an unknown SKU still fails
            // with a 404 before any event is recorded.
            BigDecimal unitPrice = catalog.priceOf(line.sku());
            eventItems.add(new OrderCreatedEvent.Item(line.sku(), line.quantity()));
            order.addItem(line.sku(), line.quantity(), unitPrice);
        }

        orders.save(order);

        // The order row and the outbox row commit together, so the window where an order
        // exists but its event was never produced is gone.
        String eventId = UUID.randomUUID().toString();
        outbox.append(Topics.ORDER_CREATED, order.getReference(), eventId, new OrderCreatedEvent(
                eventId,
                order.getReference(),
                order.getRestaurantId(),
                order.getTotal(),
                eventItems));

        return OrderResponse.from(order);
    }

    @Transactional
    public void markAwaitingPayment(String reference) {
        require(reference).awaitPayment();
    }

    @Transactional
    public void markConfirmed(String reference) {
        require(reference).confirm();
    }

    /**
     * Cancels after a stock rejection. No release is written: nothing was ever deducted,
     * and restoring stock that was never taken would inflate the inventory.
     */
    @Transactional
    public void cancelForRejectedStock(String reference, String reason) {
        require(reference).cancel(reason);
    }

    /**
     * Cancels after a payment failure and asks inventory to give the stock back. The
     * deduction is already committed in the inventory service's own database, so only an
     * opposite action can undo it. Items come from this service's own tables, since the
     * order service owns them.
     */
    @Transactional
    public void cancelForFailedPayment(String reference, String reason) {
        Order order = orders.findByReferenceWithItems(reference)
                .orElseThrow(() -> new UnknownOrderException(reference));

        order.cancel(reason);

        List<StockReleaseRequestedEvent.Item> items = order.getItems().stream()
                .map(i -> new StockReleaseRequestedEvent.Item(i.getSku(), i.getQuantity()))
                .toList();

        String eventId = UUID.randomUUID().toString();
        outbox.append(Topics.STOCK_RELEASE_REQUESTED, reference, eventId,
                new StockReleaseRequestedEvent(eventId, reference, items, reason));
    }

    @Transactional(readOnly = true)
    public OrderResponse byReference(String reference) {
        return orders.findByReferenceWithItems(reference)
                .map(OrderResponse::from)
                .orElseThrow(() -> new UnknownOrderException(reference));
    }

    private Order require(String reference) {
        return orders.findByReference(reference)
                .orElseThrow(() -> new UnknownOrderException(reference));
    }
}
