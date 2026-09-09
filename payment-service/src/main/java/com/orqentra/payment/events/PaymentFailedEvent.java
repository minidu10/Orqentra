package com.orqentra.payment.events;

public record PaymentFailedEvent(String eventId, String orderReference, String reason) {}
