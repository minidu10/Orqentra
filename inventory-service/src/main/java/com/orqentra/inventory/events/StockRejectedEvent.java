package com.orqentra.inventory.events;

public record StockRejectedEvent(String eventId, String orderReference, String reason) {}
