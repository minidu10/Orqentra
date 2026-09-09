package com.orqentra.inventory.stock;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    /**
     * All or nothing across every line: the first line without enough stock throws,
     * and the transaction rolls back whatever earlier lines had already deducted.
     */
    @Transactional
    public void deduct(List<DeductRequest.Line> lines) {
        for (DeductRequest.Line line : lines) {
            InventoryItem item = repository.findBySkuForUpdate(line.sku())
                    .orElseThrow(() -> new InsufficientStockException(line.sku(), line.quantity(), 0));

            if (!item.hasStock(line.quantity())) {
                throw new InsufficientStockException(line.sku(), line.quantity(), item.getAvailable());
            }
            item.deduct(line.quantity());
        }
    }

    @Transactional
    public void release(List<DeductRequest.Line> lines) {
        for (DeductRequest.Line line : lines) {
            repository.findBySkuForUpdate(line.sku())
                    .ifPresent(item -> item.restore(line.quantity()));
        }
    }

    @Transactional(readOnly = true)
    public int availableFor(String sku) {
        return repository.findBySku(sku)
                .map(InventoryItem::getAvailable)
                .orElse(0);
    }
}
