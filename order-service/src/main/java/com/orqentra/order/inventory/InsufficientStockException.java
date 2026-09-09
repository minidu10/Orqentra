package com.orqentra.order.inventory;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String detail) {
        super(detail);
    }
}
