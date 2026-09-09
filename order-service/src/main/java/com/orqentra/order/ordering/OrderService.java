package com.orqentra.order.ordering;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.order.catalog.CatalogService;
import com.orqentra.order.events.EventPublisher;
import com.orqentra.order.events.OrderCreatedEvent;
import com.orqentra.order.events.Topics;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CatalogService catalog;
    private final EventPublisher events;

    public OrderService(OrderRepository orders,
                        CatalogService catalog,
                        EventPublisher events) {
        this.orders = orders;
        this.catalog = catalog;
        this.events = events;
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

            // Price lookup stays ahead of the publish so an unknown SKU still fails with a
            // 404 before any event leaves this service.
            BigDecimal unitPrice = catalog.priceOf(line.sku());
            eventItems.add(new OrderCreatedEvent.Item(line.sku(), line.quantity()));
            order.addItem(line.sku(), line.quantity(), unitPrice);
        }

        // The order is saved PENDING. Inventory decides the outcome asynchronously, so
        // placing an order no longer depends on the inventory service being up.
        orders.save(order);

        events.publish(Topics.ORDER_CREATED, order.getReference(), new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                order.getReference(),
                order.getRestaurantId(),
                eventItems));

        return OrderResponse.from(order);
    }

    @Transactional
    public void markConfirmed(String reference) {
        orders.findByReference(reference)
                .orElseThrow(() -> new UnknownOrderException(reference))
                .confirm();
    }

    @Transactional
    public void markCancelled(String reference) {
        orders.findByReference(reference)
                .orElseThrow(() -> new UnknownOrderException(reference))
                .cancel();
    }

    @Transactional(readOnly = true)
    public OrderResponse byReference(String reference) {
        return orders.findByReferenceWithItems(reference)
                .map(OrderResponse::from)
                .orElseThrow(() -> new UnknownOrderException(reference));
    }
}
