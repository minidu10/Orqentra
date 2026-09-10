package com.orqentra.order.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.orqentra.order.events.PaymentSucceededEvent;
import com.orqentra.order.events.StockReservedEvent;
import com.orqentra.order.events.Topics;
import com.orqentra.order.messaging.OutboxEvent;
import com.orqentra.order.messaging.OutboxEventRepository;
import com.orqentra.order.messaging.TestEventPublisher;
import com.orqentra.order.support.AbstractIntegrationTest;
import com.orqentra.order.support.TestJwts;

import java.math.BigDecimal;

/**
 * Drives the real HTTP endpoint with a real database and a real broker: placing an order
 * writes it and its outbox row in one go, the scheduled poller delivers that row for
 * real, and the saga events that would come back from inventory/payment move the order
 * through its states exactly as production does.
 */
class OrderPlacementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEventPublisher publisher;

    private HttpEntity<String> placeOrderRequest(String restaurantRef) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + TestJwts.restaurantToken(restaurantRef));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = """
                {"items":[{"sku":"SKU-002","quantity":1}]}""";
        return new HttpEntity<>(body, headers);
    }

    @Test
    void placingAnOrder_writesTheOrderAndAnOutboxRow_inOneGo() {
        String restaurantRef = UUID.randomUUID().toString();

        ResponseEntity<OrderResponse> response = rest.postForEntity(
                "/api/orders", placeOrderRequest(restaurantRef), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reference = response.getBody().reference();
        assertThat(response.getBody().status()).isEqualTo("PENDING");

        // Both rows exist immediately, in the same database transaction that created them.
        assertThat(orderRepository.findByReference(reference)).isPresent();
        boolean hasOutboxRow = outboxEventRepository.findAll().stream()
                .anyMatch(row -> row.getTopic().equals(Topics.ORDER_CREATED)
                        && row.getMessageKey().equals(reference));
        assertThat(hasOutboxRow).isTrue();
    }

    @Test
    void thePoller_actuallyPublishesTheOutboxRow() {
        String restaurantRef = UUID.randomUUID().toString();
        ResponseEntity<OrderResponse> response = rest.postForEntity(
                "/api/orders", placeOrderRequest(restaurantRef), OrderResponse.class);
        String reference = response.getBody().reference();

        // The real @Scheduled poller runs in this Spring context; no manual trigger.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvent row = outboxEventRepository.findAll().stream()
                    .filter(r -> r.getTopic().equals(Topics.ORDER_CREATED)
                            && r.getMessageKey().equals(reference))
                    .findFirst().orElseThrow();
            assertThat(row.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void consumingEachSagaEvent_movesTheOrderThroughItsStates() {
        String restaurantRef = UUID.randomUUID().toString();
        ResponseEntity<OrderResponse> placed = rest.postForEntity(
                "/api/orders", placeOrderRequest(restaurantRef), OrderResponse.class);
        String reference = placed.getBody().reference();
        assertThat(placed.getBody().status()).isEqualTo("PENDING");

        publisher.publish(Topics.STOCK_RESERVED, reference,
                new StockReservedEvent(UUID.randomUUID().toString(), reference, new BigDecimal("4200.00")));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findByReference(reference).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.AWAITING_PAYMENT));

        publisher.publish(Topics.PAYMENT_SUCCEEDED, reference,
                new PaymentSucceededEvent(UUID.randomUUID().toString(), reference));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findByReference(reference).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.CONFIRMED));
    }

    @Test
    void placingAnOrder_forAnUnknownSku_isRejectedBeforeAnyEventIsRecorded() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + TestJwts.restaurantToken(UUID.randomUUID().toString()));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(
                """
                {"items":[{"sku":"NO-SUCH-SKU","quantity":1}]}""", headers);

        ResponseEntity<String> response = rest.postForEntity("/api/orders", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
