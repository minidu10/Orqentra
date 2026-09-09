package com.orqentra.order.ordering;

import java.util.List;

/**
 * The restaurant is deliberately absent. It comes from the verified token, so a caller
 * cannot place an order on another restaurant's behalf by editing the request body.
 */
public record PlaceOrderRequest(List<Line> items) {

    public record Line(String sku, int quantity) {}
}
