package com.orqentra.notification.events;

public record StockRejectedEvent(String eventId, String orderReference, String reason) {}
