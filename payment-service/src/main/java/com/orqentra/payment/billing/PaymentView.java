package com.orqentra.payment.billing;

import java.math.BigDecimal;

public record PaymentView(
        String orderReference,
        BigDecimal amount,
        String status,
        String failureReason) {

    public static PaymentView from(Payment payment) {
        return new PaymentView(
                payment.getOrderReference(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getFailureReason());
    }
}
