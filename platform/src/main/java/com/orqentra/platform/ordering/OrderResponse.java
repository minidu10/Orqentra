package com.orqentra.platform.ordering;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        String reference,
        String restaurantId,
        String status,
        BigDecimal total,
        List<Line> items) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) {}

    public static OrderResponse from(Order order) {
        List<Line> lines = order.getItems().stream()
                .map(i -> new Line(i.getSku(), i.getQuantity(), i.getUnitPrice()))
                .toList();

        return new OrderResponse(
                order.getReference(),
                order.getRestaurantId(),
                order.getStatus().name(),
                order.getTotal(),
                lines);
    }
}