package com.orqentra.platform.ordering;

public class UnknownOrderException extends RuntimeException {

    public UnknownOrderException(String reference) {
        super("No order with reference " + reference);
    }
}