package com.orqentra.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure entity behaviour. No Spring context, no database: {@link InventoryItem} has no
 * public constructor that sets {@code available} (only Hibernate and its own
 * {@code deduct}/{@code restore} methods do), so the starting stock level is set via
 * reflection the way {@link org.springframework.test.util.ReflectionTestUtils} is meant
 * for — this is a test concern only, nothing about the entity itself is changed.
 */
class InventoryItemTest {

    private InventoryItem itemWith(int available) {
        InventoryItem item = new InventoryItem();
        ReflectionTestUtils.setField(item, "sku", "SKU-TEST");
        ReflectionTestUtils.setField(item, "available", available);
        return item;
    }

    @Test
    void hasStock_whenSufficient() {
        assertThat(itemWith(10).hasStock(5)).isTrue();
    }

    @Test
    void hasStock_whenExactlyEnough() {
        assertThat(itemWith(5).hasStock(5)).isTrue();
    }

    @Test
    void hasStock_whenOneShort() {
        assertThat(itemWith(4).hasStock(5)).isFalse();
    }

    @Test
    void hasStock_whenZeroAvailable() {
        assertThat(itemWith(0).hasStock(1)).isFalse();
    }

    @Test
    void hasStock_zeroQuantityAlwaysSatisfied() {
        assertThat(itemWith(0).hasStock(0)).isTrue();
    }

    @Test
    void deduct_sufficientStock_reducesAvailable() {
        InventoryItem item = itemWith(10);
        item.deduct(5);
        assertThat(item.getAvailable()).isEqualTo(5);
    }

    @Test
    void deduct_exactlyEnough_leavesZero() {
        InventoryItem item = itemWith(5);
        item.deduct(5);
        assertThat(item.getAvailable()).isZero();
    }

    @Test
    void deduct_oneShort_throwsAndLeavesStockUntouched() {
        InventoryItem item = itemWith(4);

        assertThatThrownBy(() -> item.deduct(5)).isInstanceOf(IllegalStateException.class);
        assertThat(item.getAvailable()).isEqualTo(4);
    }

    @Test
    void deduct_negativeQuantity_increasesAvailable() {
        // deduct(-1) is subtracting a negative, i.e. adding. hasStock(-1) is always true
        // (available >= -1), so nothing here rejects a negative quantity — this
        // documents the current behaviour rather than asserting it is desirable.
        InventoryItem item = itemWith(5);
        item.deduct(-1);
        assertThat(item.getAvailable()).isEqualTo(6);
    }

    @Test
    void restore_addsBackToAvailable() {
        InventoryItem item = itemWith(3);
        item.restore(7);
        assertThat(item.getAvailable()).isEqualTo(10);
    }
}
