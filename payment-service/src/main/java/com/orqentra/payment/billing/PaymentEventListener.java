package com.orqentra.payment.billing;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.orqentra.payment.events.EventPublisher;
import com.orqentra.payment.events.PaymentFailedEvent;
import com.orqentra.payment.events.PaymentSucceededEvent;
import com.orqentra.payment.events.StockReservedEvent;
import com.orqentra.payment.events.Topics;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentService payments;
    private final EventPublisher events;

    public PaymentEventListener(PaymentService payments, EventPublisher events) {
        this.payments = payments;
        this.events = events;
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    public void onStockReserved(StockReservedEvent event) {
        log.info("Attempting payment of {} for order {}", event.amount(), event.orderReference());

        // A decline is a normal business outcome and leaves as an event. Nothing broader
        // is caught: a database fault must propagate so Kafka redelivers the message.
        Payment payment = payments.attempt(event.orderReference(), event.amount());

        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment failed for order {}: {}",
                    event.orderReference(), payment.getFailureReason());
            events.publish(Topics.PAYMENT_FAILED, event.orderReference(),
                    new PaymentFailedEvent(UUID.randomUUID().toString(),
                            event.orderReference(), payment.getFailureReason()));
            return;
        }

        log.info("Payment succeeded for order {}", event.orderReference());
        events.publish(Topics.PAYMENT_SUCCEEDED, event.orderReference(),
                new PaymentSucceededEvent(UUID.randomUUID().toString(), event.orderReference()));
    }
}
