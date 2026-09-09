package com.orqentra.payment.events;

public record PaymentSucceededEvent(String eventId, String orderReference) {}
