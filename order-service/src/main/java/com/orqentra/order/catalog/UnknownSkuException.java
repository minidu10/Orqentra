package com.orqentra.order.catalog;

public class UnknownSkuException extends RuntimeException {

    public UnknownSkuException(String sku) {
        super("No product with SKU " + sku);
    }
}