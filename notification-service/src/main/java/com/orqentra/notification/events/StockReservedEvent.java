package com.orqentra.notification.events;

import java.math.BigDecimal;

public record StockReservedEvent(String eventId, String orderReference, BigDecimal amount) {}
