package com.orqentra.notification.stream;

import java.time.Instant;

/**
 * What the browser actually consumes. Deliberately not a copy of any internal event: the
 * client should not have to care how the saga is decomposed into topics.
 */
public record OrderEventMessage(
        String orderReference,
        String status,
        String message,
        String reason,
        Instant timestamp) {

    public static OrderEventMessage of(String orderReference, String status, String message) {
        return new OrderEventMessage(orderReference, status, message, null, Instant.now());
    }

    public static OrderEventMessage of(String orderReference, String status,
                                       String message, String reason) {
        return new OrderEventMessage(orderReference, status, message, reason, Instant.now());
    }
}
