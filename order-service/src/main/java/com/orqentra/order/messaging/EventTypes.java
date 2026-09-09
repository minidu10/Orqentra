package com.orqentra.order.messaging;

import java.util.Map;

import com.orqentra.order.events.OrderCreatedEvent;
import com.orqentra.order.events.PaymentFailedEvent;
import com.orqentra.order.events.PaymentSucceededEvent;
import com.orqentra.order.events.StockRejectedEvent;
import com.orqentra.order.events.StockReleaseRequestedEvent;
import com.orqentra.order.events.StockReservedEvent;

/**
 * Maps an event class to the logical name carried in the __TypeId__ header. These are the
 * same names configured in spring.json.type.mapping, so every service resolves the wire
 * name to its own copy of the record.
 */
final class EventTypes {

    private static final Map<Class<?>, String> NAMES = Map.of(
            OrderCreatedEvent.class, "orderCreated",
            StockReservedEvent.class, "stockReserved",
            StockRejectedEvent.class, "stockRejected",
            PaymentSucceededEvent.class, "paymentSucceeded",
            PaymentFailedEvent.class, "paymentFailed",
            StockReleaseRequestedEvent.class, "stockReleaseRequested");

    static String logicalNameFor(Class<?> eventClass) {
        String name = NAMES.get(eventClass);
        if (name == null) {
            throw new IllegalArgumentException("No logical type name registered for " + eventClass);
        }
        return name;
    }

    private EventTypes() {}
}
