package com.orqentra.payment.billing;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_reference", nullable = false, unique = true)
    private String orderReference;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {}

    private Payment(String orderReference, BigDecimal amount, PaymentStatus status, String failureReason) {
        this.orderReference = orderReference;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
    }

    public static Payment succeeded(String orderReference, BigDecimal amount) {
        return new Payment(orderReference, amount, PaymentStatus.SUCCEEDED, null);
    }

    public static Payment failed(String orderReference, BigDecimal amount, String reason) {
        return new Payment(orderReference, amount, PaymentStatus.FAILED, reason);
    }

    public Long getId() { return id; }
    public String getOrderReference() { return orderReference; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
