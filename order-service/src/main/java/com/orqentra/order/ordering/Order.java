package com.orqentra.order.ordering;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(name = "restaurant_id", nullable = false)
    private String restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(String reference, String restaurantId) {
        this.reference = reference;
        this.restaurantId = restaurantId;
        this.status = OrderStatus.PENDING;
        this.total = BigDecimal.ZERO;
    }

    public void addItem(String sku, int quantity, BigDecimal unitPrice) {
        items.add(new OrderItem(this, sku, quantity, unitPrice));
        total = total.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * PENDING -&gt; AWAITING_PAYMENT -&gt; {CONFIRMED, CANCELLED} is the only path the saga
     * ever drives. Guarding it here, on the entity, means every caller gets the check for
     * free rather than each service method having to remember to ask first — including a
     * redelivered event arriving after the order already reached a terminal state, which
     * must never be allowed to move it again.
     */
    public void awaitPayment() {
        requireFrom(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING);
        this.status = OrderStatus.AWAITING_PAYMENT;
    }

    public void confirm() {
        requireFrom(OrderStatus.CONFIRMED, OrderStatus.AWAITING_PAYMENT);
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel(String reason) {
        requireFrom(OrderStatus.CANCELLED, OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT);
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    private void requireFrom(OrderStatus target, OrderStatus... allowed) {
        for (OrderStatus candidate : allowed) {
            if (this.status == candidate) {
                return;
            }
        }
        throw new InvalidOrderTransitionException(this.status, target);
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getRestaurantId() { return restaurantId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
}