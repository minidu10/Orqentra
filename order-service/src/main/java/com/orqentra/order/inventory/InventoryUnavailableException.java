package com.orqentra.order.inventory;

/**
 * The inventory service could not give a definite answer: a transport failure, or an
 * error status other than 409. The outcome of the deduction is unknown, which is a
 * different situation from a stock shortage the service explicitly rejected.
 */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String detail, Throwable cause) {
        super(detail, cause);
    }

    public InventoryUnavailableException(String detail) {
        super(detail);
    }
}
