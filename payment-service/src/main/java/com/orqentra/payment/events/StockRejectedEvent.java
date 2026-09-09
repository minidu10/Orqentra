package com.orqentra.payment.events;

public record StockRejectedEvent(String eventId, String orderReference, String reason) {}
