package com.orqentra.payment.billing;

public class UnknownPaymentException extends RuntimeException {

    public UnknownPaymentException(String orderReference) {
        super("No payment for order " + orderReference);
    }
}
