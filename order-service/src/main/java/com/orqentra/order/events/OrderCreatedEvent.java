package com.orqentra.order.events;

import java.util.List;

public record OrderCreatedEvent(
        String eventId,
        String orderReference,
        String restaurantId,
        List<Item> items) {

    public record Item(String sku, int quantity) {}
}
