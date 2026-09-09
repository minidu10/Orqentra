package com.orqentra.order.ordering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.order.events.PaymentFailedEvent;
import com.orqentra.order.events.PaymentSucceededEvent;
import com.orqentra.order.events.StockRejectedEvent;
import com.orqentra.order.events.StockReservedEvent;
import com.orqentra.order.events.Topics;
import com.orqentra.order.messaging.ProcessedEvents;

/**
 * The saga orchestrator. The order service owns the order's state and decides what
 * happens at each step; the other services only report what they did.
 *
 * <p>Every method is one transaction covering the dedupe claim, the state change and any
 * outbox write, so a redelivery after a crash finds the claim already committed and skips.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private static final String STOCK_RESERVED_CONSUMER = "order.stock-reserved";
    private static final String STOCK_REJECTED_CONSUMER = "order.stock-rejected";
    private static final String PAYMENT_SUCCEEDED_CONSUMER = "order.payment-succeeded";
    private static final String PAYMENT_FAILED_CONSUMER = "order.payment-failed";

    private final OrderService orders;
    private final ProcessedEvents processedEvents;

    public OrderEventListener(OrderService orders, ProcessedEvents processedEvents) {
        this.orders = orders;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    @Transactional
    public void onStockReserved(StockReservedEvent event) {
        if (!processedEvents.claim(event.eventId(), STOCK_RESERVED_CONSUMER)) {
            log.info("Skipping already processed stock.reserved event {}", event.eventId());
            return;
        }

        log.info("Stock reserved for order {}, awaiting payment of {}",
                event.orderReference(), event.amount());
        orders.markAwaitingPayment(event.orderReference());
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED)
    @Transactional
    public void onStockRejected(StockRejectedEvent event) {
        if (!processedEvents.claim(event.eventId(), STOCK_REJECTED_CONSUMER)) {
            log.info("Skipping already processed stock.rejected event {}", event.eventId());
            return;
        }

        log.info("Stock rejected for order {}: {}", event.orderReference(), event.reason());
        orders.cancelForRejectedStock(event.orderReference(), event.reason());
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCEEDED)
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (!processedEvents.claim(event.eventId(), PAYMENT_SUCCEEDED_CONSUMER)) {
            log.info("Skipping already processed payment.succeeded event {}", event.eventId());
            return;
        }

        log.info("Payment succeeded for order {}", event.orderReference());
        orders.markConfirmed(event.orderReference());
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (!processedEvents.claim(event.eventId(), PAYMENT_FAILED_CONSUMER)) {
            log.info("Skipping already processed payment.failed event {}", event.eventId());
            return;
        }

        log.info("Payment failed for order {}: {}, requesting stock release",
                event.orderReference(), event.reason());
        orders.cancelForFailedPayment(event.orderReference(), event.reason());
    }
}
