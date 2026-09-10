package com.orqentra.auth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.orqentra.auth.support.AbstractIntegrationTest;

/**
 * Drives the real registration and login endpoints against a real database — the
 * behaviour that matters here is what actually gets written and what the token actually
 * carries, not just that a handler method runs.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private HttpEntity<String> jsonBody(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void registration_hashesThePassword_andNeverStoresPlaintext() {
        String email = "chef-" + UUID.randomUUID() + "@orqentra.test";
        String plaintextPassword = "correct-horse-battery-staple";

        ResponseEntity<AuthDtos.RegisterResponse> response = rest.postForEntity(
                "/api/auth/register",
                jsonBody("""
                        {"email":"%s","password":"%s","restaurantName":"Test Kitchen"}"""
                        .formatted(email, plaintextPassword)),
                AuthDtos.RegisterResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(plaintextPassword);
        assertThat(stored.getPasswordHash()).startsWith("$2"); // BCrypt
        assertThat(passwordEncoder.matches(plaintextPassword, stored.getPasswordHash())).isTrue();
    }

    @Test
    void login_returnsATokenWithTheExpectedClaims() {
        // The seeded dev account from V1__create_identity.sql.
        ResponseEntity<AuthDtos.LoginResponse> response = rest.postForEntity(
                "/api/auth/login",
                jsonBody("""
                        {"email":"alice@orqentra.test","password":"alice-pass"}"""),
                AuthDtos.LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = response.getBody().token();
        assertThat(token).isNotBlank();

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        String claimsJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claimsJson).contains("\"sub\":\"alice@orqentra.test\"");
        assertThat(claimsJson).contains("\"role\":\"RESTAURANT\"");
        assertThat(claimsJson).contains("\"restaurantRef\"");
    }

    @Test
    void wrongPassword_andUnknownEmail_returnIdenticalResponses() {
        ResponseEntity<String> wrongPassword = rest.postForEntity(
                "/api/auth/login",
                jsonBody("""
                        {"email":"alice@orqentra.test","password":"not-the-password"}"""),
                String.class);

        ResponseEntity<String> unknownEmail = rest.postForEntity(
                "/api/auth/login",
                jsonBody("""
                        {"email":"nobody-%s@orqentra.test","password":"not-the-password"}"""
                        .formatted(UUID.randomUUID())),
                String.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Same status, same body shape: nothing here should let a caller distinguish a
        // wrong password from an account that was never registered.
        String normalizedWrongPassword = wrongPassword.getBody().replaceAll("/api/auth/login", "");
        String normalizedUnknownEmail = unknownEmail.getBody().replaceAll("/api/auth/login", "");
        assertThat(normalizedWrongPassword).isEqualTo(normalizedUnknownEmail);
    }
}
