package com.orqentra.platform.ordering;

import java.util.List;

public record PlaceOrderRequest(String restaurantId, List<Line> items) {

    public record Line(String sku, int quantity) {}
}