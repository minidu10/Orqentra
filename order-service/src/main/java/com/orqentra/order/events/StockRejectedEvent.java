package com.orqentra.order.events;

public record StockRejectedEvent(String eventId, String orderReference, String reason) {}
