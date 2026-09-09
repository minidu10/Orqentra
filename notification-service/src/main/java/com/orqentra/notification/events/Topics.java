package com.orqentra.notification.events;

public final class Topics {

    public static final String ORDER_CREATED = "order.created";
    public static final String STOCK_RESERVED = "stock.reserved";
    public static final String STOCK_REJECTED = "stock.rejected";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String STOCK_RELEASE_REQUESTED = "stock.release.requested";

    private Topics() {}
}
