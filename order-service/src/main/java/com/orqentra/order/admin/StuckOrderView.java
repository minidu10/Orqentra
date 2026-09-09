package com.orqentra.order.admin;

import java.time.Duration;
import java.time.Instant;

import com.orqentra.order.ordering.Order;

public record StuckOrderView(
        String reference,
        String status,
        Instant createdAt,
        long ageSeconds) {

    public static StuckOrderView from(Order order, Instant now) {
        return new StuckOrderView(
                order.getReference(),
                order.getStatus().name(),
                order.getCreatedAt(),
                Duration.between(order.getCreatedAt(), now).toSeconds());
    }
}
