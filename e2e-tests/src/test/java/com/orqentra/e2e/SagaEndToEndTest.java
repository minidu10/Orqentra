package com.orqentra.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * Starts real Postgres and a real Redpanda via Testcontainers, then runs each of the
 * three saga services — order, inventory, payment — as its own separate JVM process from
 * its actually-built jar, wired to those containers exactly the way a real deployment
 * would be, over real HTTP and real Kafka.
 *
 * <p>The auth service and the gateway are deliberately not part of this test. Each
 * service verifies its JWT locally against the shared dev secret, so a token minted here
 * with that same secret — exactly what {@code TestJwts} does in each service's own test
 * suite — is indistinguishable from one the real auth service would have issued. Standing
 * up all six services as separate processes for a saga test that is about the order,
 * inventory and payment services talking to each other would only add startup time
 * without exercising anything the per-service test suites do not already cover for auth
 * and the gateway individually.
 */
class SagaEndToEndTest {

    private static final Duration SAGA_TIMEOUT = Duration.ofSeconds(30);
    private static final String JWT_SECRET = "dev-only-secret-change-me-in-production-0123456789";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static PostgreSQLContainer orderDb;
    private static PostgreSQLContainer inventoryDb;
    private static PostgreSQLContainer paymentDb;
    private static RedpandaContainer redpanda;

    private static Process orderProcess;
    private static Process inventoryProcess;
    private static Process paymentProcess;

    private static final int ORDER_PORT = 18084;
    private static final int INVENTORY_PORT = 18081;
    private static final int PAYMENT_PORT = 18082;

    @BeforeAll
    static void startEverything() throws Exception {
        orderDb = new PostgreSQLContainer("postgres:16").withDatabaseName("orders");
        inventoryDb = new PostgreSQLContainer("postgres:16").withDatabaseName("inventory");
        paymentDb = new PostgreSQLContainer("postgres:16").withDatabaseName("payments");
        redpanda = new RedpandaContainer("redpandadata/redpanda:v24.2.7");

        // Independent containers, so start them concurrently rather than paying each
        // one's startup cost in sequence.
        orderDb.start();
        inventoryDb.start();
        paymentDb.start();
        redpanda.start();

        Path root = Path.of(System.getProperty("user.dir")).getParent();

        inventoryProcess = startService(
                root.resolve("inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar"),
                INVENTORY_PORT, inventoryDb, "inventory-service");
        paymentProcess = startService(
                root.resolve("payment-service/target/payment-service-0.0.1-SNAPSHOT.jar"),
                PAYMENT_PORT, paymentDb, "payment-service");
        orderProcess = startService(
                root.resolve("order-service/target/order-service-0.0.1-SNAPSHOT.jar"),
                ORDER_PORT, orderDb, "order-service");

        awaitHealthy(ORDER_PORT);
        awaitHealthy(INVENTORY_PORT);
        awaitHealthy(PAYMENT_PORT);
    }

    @AfterAll
    static void stopEverything() {
        for (Process p : new Process[] { orderProcess, inventoryProcess, paymentProcess }) {
            if (p != null) {
                p.destroy();
            }
        }
    }

    private static Process startService(Path jar, int port, PostgreSQLContainer db, String groupId)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "java", "-jar", jar.toAbsolutePath().toString(),
                "--server.port=" + port,
                "--spring.datasource.url=" + db.getJdbcUrl(),
                "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(),
                "--spring.kafka.bootstrap-servers=" + redpanda.getBootstrapServers(),
                "--spring.kafka.consumer.group-id=" + groupId,
                "--management.tracing.export.enabled=false");
        builder.redirectOutput(Path.of("target", groupId + "-e2e.log").toFile());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    /** Polls the actuator health endpoint rather than sleeping a fixed guess at startup time. */
    private static void awaitHealthy(int port) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).until(() -> {
            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception ex) {
                return false;
            }
        });
    }

    private static String token(String restaurantRef) throws Exception {
        MACSigner signer = new MACSigner(JWT_SECRET.getBytes());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("restaurant@e2e.test")
                .claim("restaurantRef", restaurantRef)
                .claim("role", "RESTAURANT")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private JsonNode placeOrder(String token, String sku, int quantity) throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "items", java.util.List.of(Map.of("sku", sku, "quantity", quantity))));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + ORDER_PORT + "/api/orders"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return JSON.readTree(response.body());
    }

    private JsonNode getOrder(String token, String reference) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + ORDER_PORT + "/api/orders/" + reference))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private int stockOf(String token, String sku) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + INVENTORY_PORT + "/api/inventory/" + sku))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).get("available").asInt();
    }

    private JsonNode pollOrderUntil(String token, String reference, String expectedStatus) {
        JsonNode[] holder = new JsonNode[1];
        await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            JsonNode order = getOrder(token, reference);
            holder[0] = order;
            assertThat(order.get("status").asText()).isEqualTo(expectedStatus);
        });
        return holder[0];
    }

    @Test
    void happyPath_orderReachesConfirmed_andStockIsReducedByTheOrderedAmount() throws Exception {
        String restaurantRef = UUID.randomUUID().toString();
        String token = token(restaurantRef);

        int before = stockOf(token, "SKU-002");

        JsonNode placed = placeOrder(token, "SKU-002", 1);
        assertThat(placed.get("status").asText()).isEqualTo("PENDING");
        String reference = placed.get("reference").asText();

        pollOrderUntil(token, reference, "CONFIRMED");

        await().atMost(SAGA_TIMEOUT).untilAsserted(() ->
                assertThat(stockOf(token, "SKU-002")).isEqualTo(before - 1));
    }

    @Test
    void paymentCompensation_orderIsCancelledWithAPaymentReason_andStockReturnsToExactlyItsPreOrderValue()
            throws Exception {
        String restaurantRef = UUID.randomUUID().toString();
        String token = token(restaurantRef);

        int before = stockOf(token, "SKU-001");

        // 5 x 12500.00 = 62500.00, over the 50000 decline threshold.
        JsonNode placed = placeOrder(token, "SKU-001", 5);
        String reference = placed.get("reference").asText();

        JsonNode settled = pollOrderUntil(token, reference, "CANCELLED");
        assertThat(settled.get("cancellationReason").asText()).isEqualTo("Amount exceeds card limit");

        // The exact value, not merely "some release event was published": the stock was
        // genuinely deducted by the reservation and genuinely restored by the release,
        // and those two real database writes must net out to nothing.
        await().atMost(SAGA_TIMEOUT).untilAsserted(() ->
                assertThat(stockOf(token, "SKU-001")).isEqualTo(before));
    }

    @Test
    void stockRejection_orderIsCancelledWithAStockReason_andNoReleaseIsEverPublished() throws Exception {
        String restaurantRef = UUID.randomUUID().toString();
        String token = token(restaurantRef);

        int before = stockOf(token, "SKU-003"); // seeded low

        JsonNode placed = placeOrder(token, "SKU-003", 999);
        String reference = placed.get("reference").asText();

        JsonNode settled = pollOrderUntil(token, reference, "CANCELLED");
        assertThat(settled.get("cancellationReason").asText()).contains("Insufficient stock");

        // Nothing was ever reserved, so nothing should ever be released, and the stock
        // level must never have moved at all — not even briefly.
        assertThat(stockOf(token, "SKU-003")).isEqualTo(before);
    }
}
