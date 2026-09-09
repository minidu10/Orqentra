package com.orqentra.notification.events;

public record PaymentSucceededEvent(String eventId, String orderReference) {}
