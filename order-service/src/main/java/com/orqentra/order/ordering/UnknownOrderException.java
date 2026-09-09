package com.orqentra.order.ordering;

public class UnknownOrderException extends RuntimeException {

    public UnknownOrderException(String reference) {
        super("No order with reference " + reference);
    }
}