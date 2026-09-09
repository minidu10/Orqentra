package com.orqentra.notification.events;

import java.util.List;

public record StockReleaseRequestedEvent(
        String eventId,
        String orderReference,
        List<Item> items,
        String reason) {

    public record Item(String sku, int quantity) {}
}
