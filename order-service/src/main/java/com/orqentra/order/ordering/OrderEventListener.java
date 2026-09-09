package com.orqentra.order.ordering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.orqentra.order.events.PaymentFailedEvent;
import com.orqentra.order.events.PaymentSucceededEvent;
import com.orqentra.order.events.StockRejectedEvent;
import com.orqentra.order.events.StockReservedEvent;
import com.orqentra.order.events.Topics;

/**
 * The saga orchestrator. The order service owns the order's state and decides what
 * happens at each step; the other services only report what they did.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderService orders;

    public OrderEventListener(OrderService orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    public void onStockReserved(StockReservedEvent event) {
        log.info("Stock reserved for order {}, awaiting payment of {}",
                event.orderReference(), event.amount());
        orders.markAwaitingPayment(event.orderReference());
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED)
    public void onStockRejected(StockRejectedEvent event) {
        log.info("Stock rejected for order {}: {}", event.orderReference(), event.reason());
        orders.cancelForRejectedStock(event.orderReference(), event.reason());
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCEEDED)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Payment succeeded for order {}", event.orderReference());
        orders.markConfirmed(event.orderReference());
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Payment failed for order {}: {}, requesting stock release",
                event.orderReference(), event.reason());
        orders.cancelForFailedPayment(event.orderReference(), event.reason());
    }
}
