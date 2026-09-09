package com.orqentra.order.events;

public record PaymentFailedEvent(String eventId, String orderReference, String reason) {}
