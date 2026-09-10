package com.orqentra.order.support;

import java.time.Instant;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Mints HS256 tokens signed with the same dev secret every service defaults to
 * ({@code orqentra.jwt.secret}), so tests can call a real, fully-secured HTTP endpoint
 * without needing the auth service running. Nimbus is already on the classpath
 * transitively via spring-boot-starter-oauth2-resource-server, the same library each
 * service uses to verify tokens, so this signs with exactly the mechanism the other side
 * expects.
 */
public final class TestJwts {

    private static final String DEV_SECRET = "dev-only-secret-change-me-in-production-0123456789";

    public static String token(String email, String restaurantRef, String role) {
        try {
            JWSSigner signer = new MACSigner(DEV_SECRET.getBytes());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(email)
                    .claim("restaurantRef", restaurantRef)
                    .claim("role", role)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not mint a test JWT", ex);
        }
    }

    public static String restaurantToken(String restaurantRef) {
        return token("restaurant@test.orqentra", restaurantRef, "RESTAURANT");
    }

    public static String adminToken() {
        return token("admin@test.orqentra", "00000000-0000-0000-0000-000000000000", "ADMIN");
    }

    private TestJwts() {}
}
