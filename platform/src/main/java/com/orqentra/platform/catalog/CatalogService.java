package com.orqentra.platform.catalog;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ProductRepository repository;

    public CatalogService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BigDecimal priceOf(String sku) {
        return repository.findBySku(sku)
                .map(Product::getPrice)
                .orElseThrow(() -> new UnknownSkuException(sku));
    }
}