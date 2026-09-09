package com.orqentra.payment.billing;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    static final String DECLINE_REASON = "Amount exceeds card limit";

    private final PaymentRepository payments;
    private final PaymentProperties properties;

    public PaymentService(PaymentRepository payments, PaymentProperties properties) {
        this.payments = payments;
        this.properties = properties;
    }

    /**
     * Deterministic stand-in for a card network: anything over the configured limit is
     * declined. Returns the saved record so the caller can publish the matching event.
     */
    @Transactional
    public Payment attempt(String orderReference, BigDecimal amount) {
        boolean declined = amount.compareTo(properties.declineAbove()) > 0;

        Payment payment = declined
                ? Payment.failed(orderReference, amount, DECLINE_REASON)
                : Payment.succeeded(orderReference, amount);

        return payments.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentView byOrderReference(String orderReference) {
        return payments.findByOrderReference(orderReference)
                .map(PaymentView::from)
                .orElseThrow(() -> new UnknownPaymentException(orderReference));
    }
}
