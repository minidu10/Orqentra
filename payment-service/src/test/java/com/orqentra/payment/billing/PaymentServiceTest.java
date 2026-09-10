package com.orqentra.payment.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The decline rule in isolation. The repository is mocked here — not in an integration
 * test — because the only thing this test cares about is the threshold comparison, and a
 * real database would only add noise to that question.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final BigDecimal LIMIT = new BigDecimal("50000");

    @Mock
    private PaymentRepository repository;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, new PaymentProperties(LIMIT));
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void belowTheLimit_succeeds() {
        Payment payment = service.attempt("REF-1", new BigDecimal("49999.99"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void exactlyAtTheLimit_succeeds() {
        // The rule is "strictly greater than", so the boundary value itself must pass.
        Payment payment = service.attempt("REF-2", LIMIT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void aboveTheLimit_isDeclined() {
        Payment payment = service.attempt("REF-3", new BigDecimal("50000.01"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Amount exceeds card limit");
    }

    @Test
    void everyAttempt_isPersisted() {
        service.attempt("REF-4", new BigDecimal("1.00"));
        verify(repository).save(any(Payment.class));
    }
}
