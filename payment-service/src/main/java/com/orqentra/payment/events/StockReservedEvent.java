package com.orqentra.payment.events;

import java.math.BigDecimal;

/**
 * Carries the order total so payment can charge without asking the order service for it.
 */
public record StockReservedEvent(String eventId, String orderReference, BigDecimal amount) {}
