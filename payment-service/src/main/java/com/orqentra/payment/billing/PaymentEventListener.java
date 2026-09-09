package com.orqentra.payment.billing;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.payment.events.PaymentFailedEvent;
import com.orqentra.payment.events.PaymentSucceededEvent;
import com.orqentra.payment.events.StockReservedEvent;
import com.orqentra.payment.events.Topics;
import com.orqentra.payment.messaging.OutboxWriter;
import com.orqentra.payment.messaging.ProcessedEvents;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private static final String STOCK_RESERVED_CONSUMER = "payment.stock-reserved";

    private final PaymentService payments;
    private final OutboxWriter outbox;
    private final ProcessedEvents processedEvents;

    public PaymentEventListener(PaymentService payments,
                                OutboxWriter outbox,
                                ProcessedEvents processedEvents) {
        this.payments = payments;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
    }

    /**
     * One transaction covers the dedupe claim, the payment row and the outbox row. Without
     * the claim a redelivery would hit the unique constraint on order_reference and retry
     * forever; with it the duplicate is recognised and skipped.
     */
    @KafkaListener(topics = Topics.STOCK_RESERVED)
    @Transactional
    public void onStockReserved(StockReservedEvent event) {
        if (!processedEvents.claim(event.eventId(), STOCK_RESERVED_CONSUMER)) {
            log.info("Skipping already processed stock.reserved event {}", event.eventId());
            return;
        }

        log.info("Attempting payment of {} for order {}", event.amount(), event.orderReference());

        // A decline is a normal business outcome and leaves as an event. Nothing broader
        // is caught: a database fault must propagate so the error handler can retry.
        Payment payment = payments.attempt(event.orderReference(), event.amount());

        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment failed for order {}: {}",
                    event.orderReference(), payment.getFailureReason());
            String failedId = UUID.randomUUID().toString();
            outbox.append(Topics.PAYMENT_FAILED, event.orderReference(), failedId,
                    new PaymentFailedEvent(failedId, event.orderReference(),
                            payment.getFailureReason()));
            return;
        }

        log.info("Payment succeeded for order {}", event.orderReference());
        String succeededId = UUID.randomUUID().toString();
        outbox.append(Topics.PAYMENT_SUCCEEDED, event.orderReference(), succeededId,
                new PaymentSucceededEvent(succeededId, event.orderReference()));
    }
}
