package com.orqentra.order.events;

public record PaymentSucceededEvent(String eventId, String orderReference) {}
