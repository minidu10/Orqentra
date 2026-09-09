package com.orqentra.inventory.events;

public record PaymentSucceededEvent(String eventId, String orderReference) {}
