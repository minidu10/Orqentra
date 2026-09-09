package com.orqentra.order.events;

public record StockReservedEvent(String eventId, String orderReference) {}
