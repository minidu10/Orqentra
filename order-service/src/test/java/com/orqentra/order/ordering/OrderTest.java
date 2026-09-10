package com.orqentra.order.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Pure entity behaviour: total calculation and the state machine. No Spring context, no
 * database — {@link Order} has everything it needs in its own constructor and methods.
 */
class OrderTest {

    private Order newOrder() {
        return new Order("REF-1", "RESTAURANT-1");
    }

    @Test
    void total_accumulatesAcrossSeveralLines() {
        Order order = newOrder();

        order.addItem("SKU-001", 2, new BigDecimal("12500.00"));
        order.addItem("SKU-002", 1, new BigDecimal("4200.00"));
        order.addItem("SKU-003", 3, new BigDecimal("1850.00"));

        // 2*12500 + 1*4200 + 3*1850 = 25000 + 4200 + 5550 = 34750
        assertThat(order.getTotal()).isEqualByComparingTo("34750.00");
        assertThat(order.getItems()).hasSize(3);
    }

    @Test
    void total_usesTheStoredUnitPrice_notACurrentCatalogPrice() {
        // addItem takes the price as an argument rather than looking one up, precisely so
        // that a later catalog price change cannot retroactively change what an already
        // placed order is charged. Passing two different prices for the same SKU in one
        // order proves the entity trusts the caller's price rather than re-deriving it.
        Order order = newOrder();

        order.addItem("SKU-001", 1, new BigDecimal("100.00"));
        order.addItem("SKU-001", 1, new BigDecimal("999.00")); // "catalog price changed"

        assertThat(order.getTotal()).isEqualByComparingTo("1099.00");
    }

    @Test
    void newOrder_startsPending() {
        assertThat(newOrder().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void happyPath_pendingToAwaitingPaymentToConfirmed() {
        Order order = newOrder();

        order.awaitPayment();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        order.confirm();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancel_fromPending_isAllowed() {
        Order order = newOrder();
        order.cancel("Insufficient stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Insufficient stock");
    }

    @Test
    void cancel_fromAwaitingPayment_isAllowed() {
        Order order = newOrder();
        order.awaitPayment();
        order.cancel("Amount exceeds card limit");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void confirm_afterCancel_isRejected() {
        Order order = newOrder();
        order.awaitPayment();
        order.cancel("Amount exceeds card limit");

        assertThatThrownBy(order::confirm)
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessageContaining("CANCELLED")
                .hasMessageContaining("CONFIRMED");

        // The rejected transition must not have partially applied.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_afterConfirm_isRejected() {
        Order order = newOrder();
        order.awaitPayment();
        order.confirm();

        assertThatThrownBy(() -> order.cancel("too late"))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getCancellationReason()).isNull();
    }

    @Test
    void confirm_withoutAwaitingPaymentFirst_isRejected() {
        Order order = newOrder();

        assertThatThrownBy(order::confirm).isInstanceOf(InvalidOrderTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void awaitPayment_calledTwice_isRejected() {
        Order order = newOrder();
        order.awaitPayment();

        assertThatThrownBy(order::awaitPayment).isInstanceOf(InvalidOrderTransitionException.class);
    }
}
