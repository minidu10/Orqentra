package com.orqentra.payment.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.orqentra.payment.events.StockReservedEvent;
import com.orqentra.payment.events.Topics;
import com.orqentra.payment.messaging.OutboxEventRepository;
import com.orqentra.payment.messaging.TestEventPublisher;
import com.orqentra.payment.support.AbstractIntegrationTest;

/**
 * Drives the real listener against a real broker and database: publishes stock.reserved
 * exactly as the inventory service would, and checks both the payments table and the
 * outbox row the listener writes for the matching payment.succeeded / payment.failed.
 */
class PaymentEventListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestEventPublisher publisher;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private boolean outboxHas(String topic, String key) {
        return outboxEventRepository.findAll().stream()
                .anyMatch(row -> row.getTopic().equals(topic) && row.getMessageKey().equals(key));
    }

    @Test
    void stockReserved_underTheThreshold_succeedsAndRecordsThePayment() {
        String orderRef = UUID.randomUUID().toString();

        publisher.publish(Topics.STOCK_RESERVED, orderRef,
                new StockReservedEvent(UUID.randomUUID().toString(), orderRef, new BigDecimal("25000.00")));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(paymentRepository.findByOrderReference(orderRef)).isPresent());

        Payment payment = paymentRepository.findByOrderReference(orderRef).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getAmount()).isEqualByComparingTo("25000.00");
        assertThat(outboxHas(Topics.PAYMENT_SUCCEEDED, orderRef)).isTrue();
        assertThat(outboxHas(Topics.PAYMENT_FAILED, orderRef)).isFalse();
    }

    @Test
    void stockReserved_aboveTheThreshold_isDeclinedWithAReason() {
        String orderRef = UUID.randomUUID().toString();

        publisher.publish(Topics.STOCK_RESERVED, orderRef,
                new StockReservedEvent(UUID.randomUUID().toString(), orderRef, new BigDecimal("62500.00")));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(paymentRepository.findByOrderReference(orderRef)).isPresent());

        Payment payment = paymentRepository.findByOrderReference(orderRef).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Amount exceeds card limit");
        assertThat(outboxHas(Topics.PAYMENT_FAILED, orderRef)).isTrue();
        assertThat(outboxHas(Topics.PAYMENT_SUCCEEDED, orderRef)).isFalse();
    }
}
