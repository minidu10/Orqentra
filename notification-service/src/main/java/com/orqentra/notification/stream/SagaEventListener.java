package com.orqentra.notification.stream;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.orqentra.notification.events.OrderCreatedEvent;
import com.orqentra.notification.events.PaymentFailedEvent;
import com.orqentra.notification.events.PaymentSucceededEvent;
import com.orqentra.notification.events.StockRejectedEvent;
import com.orqentra.notification.events.StockReleaseRequestedEvent;
import com.orqentra.notification.events.StockReservedEvent;
import com.orqentra.notification.events.Topics;

/**
 * Consumes the saga read-only. It publishes nothing and changes nothing, so adding it
 * cannot affect the flow the other services run.
 */
@Component
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);

    /** The client cares about the order's state, not which topic carried the news. */
    private static final String ORDER_STATUS = "order.status";
    private static final String STOCK_RELEASED = "stock.released";

    private final EmitterRegistry registry;
    private final OrderOwnershipView ownership;

    public SagaEventListener(EmitterRegistry registry, OrderOwnershipView ownership) {
        this.registry = registry;
        this.ownership = ownership;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        // The only event carrying the restaurant, so this is where the read model is built.
        ownership.record(event.orderReference(), event.restaurantId());

        registry.send(event.restaurantId(), ORDER_STATUS, OrderEventMessage.of(
                event.orderReference(), "PENDING", "Order received"));
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    public void onStockReserved(StockReservedEvent event) {
        push(event.orderReference(), ORDER_STATUS, OrderEventMessage.of(
                event.orderReference(), "AWAITING_PAYMENT", "Stock reserved, taking payment"));
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED)
    public void onStockRejected(StockRejectedEvent event) {
        push(event.orderReference(), ORDER_STATUS, OrderEventMessage.of(
                event.orderReference(), "CANCELLED", "Not enough stock", event.reason()));
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCEEDED)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        push(event.orderReference(), ORDER_STATUS, OrderEventMessage.of(
                event.orderReference(), "CONFIRMED", "Payment accepted, order confirmed"));
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        push(event.orderReference(), ORDER_STATUS, OrderEventMessage.of(
                event.orderReference(), "CANCELLED", "Payment declined", event.reason()));
    }

    @KafkaListener(topics = Topics.STOCK_RELEASE_REQUESTED)
    public void onStockReleaseRequested(StockReleaseRequestedEvent event) {
        push(event.orderReference(), STOCK_RELEASED, OrderEventMessage.of(
                event.orderReference(), null, "Reserved stock returned", event.reason()));
    }

    /**
     * Routes an event that carries only an order reference.
     *
     * <p>An unknown order is logged and dropped, never thrown. The mapping is in memory,
     * so anything created before the last restart is permanently unroutable here, and
     * retrying it would block the partition for every order queued behind it.
     */
    private void push(String orderReference, String eventName, OrderEventMessage message) {
        Optional<String> restaurant = ownership.restaurantFor(orderReference);

        if (restaurant.isEmpty()) {
            log.warn("No restaurant mapping for order {}, dropping {} event. The order was "
                    + "most likely created before this service last started.",
                    orderReference, eventName);
            return;
        }

        registry.send(restaurant.get(), eventName, message);
    }
}
