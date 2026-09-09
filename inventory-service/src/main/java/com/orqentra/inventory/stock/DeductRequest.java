package com.orqentra.inventory.stock;

import java.util.List;

public record DeductRequest(List<Line> items) {

    public record Line(String sku, int quantity) {}
}
