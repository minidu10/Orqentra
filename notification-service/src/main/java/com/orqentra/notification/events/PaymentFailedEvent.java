package com.orqentra.notification.events;

public record PaymentFailedEvent(String eventId, String orderReference, String reason) {}
