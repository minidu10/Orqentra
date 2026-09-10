package com.orqentra.auth.support;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

/**
 * Shared Testcontainers fixture for CLASS integration tests.
 *
 * <p>Started once in a static initializer and never stopped by this class, so every
 * {@code @SpringBootTest} class that extends this base and shares its configuration
 * reuses the same cached Spring context — and therefore the same running container —
 * instead of paying the startup cost per test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }
}
