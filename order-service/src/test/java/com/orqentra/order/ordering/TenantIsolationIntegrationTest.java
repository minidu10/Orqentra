package com.orqentra.order.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.orqentra.order.support.AbstractIntegrationTest;
import com.orqentra.order.support.TestJwts;

/**
 * A leak here would let one restaurant enumerate or read another's orders, which is the
 * one property this whole layer of the system exists to prevent.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders authHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String placeOrderAs(String restaurantRef) {
        HttpEntity<String> request = new HttpEntity<>(
                """
                {"items":[{"sku":"SKU-001","quantity":1}]}""",
                authHeaders(TestJwts.restaurantToken(restaurantRef)));
        ResponseEntity<OrderResponse> response =
                rest.postForEntity("/api/orders", request, OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().reference();
    }

    @Test
    void anotherRestaurantsToken_readingMyOrder_getsNotFound_notForbidden() {
        String restaurantA = UUID.randomUUID().toString();
        String restaurantB = UUID.randomUUID().toString();

        String orderReference = placeOrderAs(restaurantA);

        HttpEntity<Void> requestAsB = new HttpEntity<>(authHeaders(TestJwts.restaurantToken(restaurantB)));
        ResponseEntity<String> response = rest.exchange(
                "/api/orders/" + orderReference, org.springframework.http.HttpMethod.GET,
                requestAsB, String.class);

        // 404, not 403: a 403 would confirm the reference is real, which is enough on its
        // own to let one tenant enumerate another's order references.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theOwningRestaurant_canReadItsOwnOrder() {
        String restaurantA = UUID.randomUUID().toString();
        String orderReference = placeOrderAs(restaurantA);

        HttpEntity<Void> requestAsA = new HttpEntity<>(authHeaders(TestJwts.restaurantToken(restaurantA)));
        ResponseEntity<OrderResponse> response = rest.exchange(
                "/api/orders/" + orderReference, org.springframework.http.HttpMethod.GET,
                requestAsA, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().reference()).isEqualTo(orderReference);
    }
}
