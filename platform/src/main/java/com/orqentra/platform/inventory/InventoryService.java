package com.orqentra.platform.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void deduct(String sku, int quantity) {
        InventoryItem item = repository.findBySkuForUpdate(sku)
                .orElseThrow(() -> new InsufficientStockException(sku, quantity, 0));

        if (!item.hasStock(quantity)) {
            throw new InsufficientStockException(sku, quantity, item.getAvailable());
        }
        item.deduct(quantity);
    }

    @Transactional(readOnly = true)
    public int availableFor(String sku) {
        return repository.findBySku(sku)
                .map(InventoryItem::getAvailable)
                .orElse(0);
    }
}