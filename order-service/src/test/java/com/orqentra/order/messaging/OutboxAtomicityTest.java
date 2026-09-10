package com.orqentra.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orqentra.order.ordering.OrderRepository;
import com.orqentra.order.ordering.OrderResponse;
import com.orqentra.order.ordering.OrderService;
import com.orqentra.order.ordering.PlaceOrderRequest;
import com.orqentra.order.support.AbstractIntegrationTest;

/**
 * OrderService.place() writes the order row and the outbox row as two separate
 * repository calls inside one {@code @Transactional} method. This proves they really are
 * atomic — not "usually saved together" but incapable of being saved apart — by forcing
 * the surrounding transaction to roll back after both writes have happened and checking
 * that both vanish together, then proving the positive case: an ordinary commit leaves
 * both present together.
 *
 * <p>The forced rollback is not Spring Test's own test-transaction rollback (which would
 * roll back regardless of whether atomicity actually holds, making the check vacuous).
 * {@code place()} is invoked inside a {@link TransactionTemplate}-managed transaction
 * that it joins via ordinary REQUIRED propagation, and the rollback is triggered from
 * outside it, the same way an unrelated failure later in a larger transaction would.
 */
class OutboxAtomicityTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String restaurantRef) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("test@orqentra.test")
                .claim("restaurantRef", restaurantRef)
                .claim("role", "RESTAURANT")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_RESTAURANT"));
        AbstractAuthenticationToken auth = new JwtAuthenticationToken(jwt, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private PlaceOrderRequest oneLineOrder() {
        return new PlaceOrderRequest(List.of(new PlaceOrderRequest.Line("SKU-001", 1)));
    }

    private boolean hasOutboxRowFor(String reference) {
        return outboxEventRepository.findAll().stream()
                .anyMatch(row -> row.getMessageKey().equals(reference));
    }

    @Test
    void whenTheTransactionRollsBack_neitherTheOrderNorTheOutboxRowExists() {
        authenticateAs(UUID.randomUUID().toString());
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        String reference = transactionTemplate.execute(status -> {
            OrderResponse response = orderService.place(oneLineOrder());
            // Forced from outside the write itself — this is standing in for "some
            // unrelated failure occurred later in the same transaction", which is exactly
            // the scenario the outbox pattern has to survive.
            status.setRollbackOnly();
            return response.reference();
        });

        assertThat(orderRepository.findByReference(reference)).isEmpty();
        assertThat(hasOutboxRowFor(reference)).isFalse();
    }

    @Test
    void whenTheTransactionCommits_bothTheOrderAndTheOutboxRowExist() {
        authenticateAs(UUID.randomUUID().toString());
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        String reference = transactionTemplate.execute(status ->
                orderService.place(oneLineOrder()).reference());

        assertThat(orderRepository.findByReference(reference)).isPresent();
        assertThat(hasOutboxRowFor(reference)).isTrue();
    }
}
