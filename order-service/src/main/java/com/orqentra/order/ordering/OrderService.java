package com.orqentra.order.ordering;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.order.catalog.CatalogService;
import com.orqentra.order.events.OrderCreatedEvent;
import com.orqentra.order.events.StockReleaseRequestedEvent;
import com.orqentra.order.events.Topics;
import com.orqentra.order.messaging.OutboxWriter;
import com.orqentra.order.security.CurrentUser;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CatalogService catalog;
    private final OutboxWriter outbox;
    private final CurrentUser currentUser;

    public OrderService(OrderRepository orders,
                        CatalogService catalog,
                        OutboxWriter outbox,
                        CurrentUser currentUser) {
        this.orders = orders;
        this.catalog = catalog;
        this.outbox = outbox;
        this.currentUser = currentUser;
    }

    @Transactional
    public OrderResponse place(PlaceOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }

        // The restaurant comes from the verified token, never from the request.
        String restaurantRef = currentUser.restaurantRef();
        Order order = new Order(UUID.randomUUID().toString(), restaurantRef);
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

    /**
     * Reads one order for the caller.
     *
     * <p>An order owned by another restaurant is reported as unknown, not forbidden. A 403
     * would confirm that the reference is real, which is enough to enumerate other
     * tenants' order references. The check lives here rather than in the controller so it
     * still applies to any other entry point that reaches this method.
     */
    @Transactional(readOnly = true)
    public OrderResponse byReferenceForCaller(String reference) {
        Order order = orders.findByReferenceWithItems(reference)
                .orElseThrow(() -> new UnknownOrderException(reference));

        if (!currentUser.isAdmin() && !order.getRestaurantId().equals(currentUser.restaurantRef())) {
            throw new UnknownOrderException(reference);
        }

        return OrderResponse.from(order);
    }

    /** Lists the calling restaurant's own orders, most recent first. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> ownOrders(Pageable pageable) {
        return orders.findByRestaurantIdOrderByIdDesc(currentUser.restaurantRef(), pageable)
                .map(OrderResponse::from);
    }

    private Order require(String reference) {
        return orders.findByReference(reference)
                .orElseThrow(() -> new UnknownOrderException(reference));
    }
}
