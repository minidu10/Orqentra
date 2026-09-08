package com.orqentra.platform.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private int available;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected InventoryItem() {}

    public boolean hasStock(int quantity) {
        return available >= quantity;
    }

    public void deduct(int quantity) {
        if (!hasStock(quantity)) {
            throw new IllegalStateException("Not enough stock for " + sku);
        }
        available -= quantity;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public int getAvailable() { return available; }
}