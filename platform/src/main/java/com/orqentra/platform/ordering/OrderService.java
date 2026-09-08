package com.orqentra.platform.ordering;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.platform.catalog.CatalogService;
import com.orqentra.platform.inventory.InventoryService;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CatalogService catalog;
    private final InventoryService inventory;

    public OrderService(OrderRepository orders,
                        CatalogService catalog,
                        InventoryService inventory) {
        this.orders = orders;
        this.catalog = catalog;
        this.inventory = inventory;
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

        for (PlaceOrderRequest.Line line : request.items()) {
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for " + line.sku());
            }

            BigDecimal unitPrice = catalog.priceOf(line.sku());
            inventory.deduct(line.sku(), line.quantity());
            order.addItem(line.sku(), line.quantity(), unitPrice);
        }

        order.confirm();
        orders.save(order);

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse byReference(String reference) {
        return orders.findByReferenceWithItems(reference)
                .map(OrderResponse::from)
                .orElseThrow(() -> new UnknownOrderException(reference));
    }
}