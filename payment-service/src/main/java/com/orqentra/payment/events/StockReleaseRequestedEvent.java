package com.orqentra.payment.events;

import java.util.List;

/**
 * The compensating event. By the time this is published the inventory service has already
 * committed its deduction, so no transaction can undo it — only an opposite action can.
 */
public record StockReleaseRequestedEvent(
        String eventId,
        String orderReference,
        List<Item> items,
        String reason) {

    public record Item(String sku, int quantity) {}
}
