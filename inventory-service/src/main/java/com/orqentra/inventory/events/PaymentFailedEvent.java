package com.orqentra.inventory.events;

public record PaymentFailedEvent(String eventId, String orderReference, String reason) {}
