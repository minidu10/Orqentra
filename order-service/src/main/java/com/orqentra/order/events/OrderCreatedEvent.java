package com.orqentra.order.events;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(
        String eventId,
        String orderReference,
        String restaurantId,
        BigDecimal amount,
        List<Item> items) {

    public record Item(String sku, int quantity) {}
}
