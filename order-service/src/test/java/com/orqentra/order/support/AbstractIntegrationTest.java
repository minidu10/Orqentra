package com.orqentra.order.support;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

/**
 * Shared Testcontainers fixture for CLASS integration tests.
 *
 * <p>The containers are started once, in a static initializer, and deliberately never
 * stopped by this test class. Every {@code @SpringBootTest} class in this module that
 * extends this base declares the same configuration, so Spring's test context cache
 * reuses one ApplicationContext (and therefore one already-running pair of containers)
 * across all of them. Without this, each test class would pay the ~5-10s container
 * startup cost on its own and a full run would take many minutes.
 *
 * <p>Real Postgres and a real Redpanda broker, not mocks: the behaviour under test is
 * whether a JPA transaction and a Kafka consumer really do what the production
 * configuration says they do, and a mock cannot answer that question.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16");

    @ServiceConnection
    static final RedpandaContainer REDPANDA =
            new RedpandaContainer("redpandadata/redpanda:v24.2.7");

    static {
        POSTGRES.start();
        REDPANDA.start();
    }
}
