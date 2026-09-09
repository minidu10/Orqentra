package com.orqentra.auth.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.orqentra.auth.identity.AuthDtos;
import com.orqentra.auth.identity.User;

@Component
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public AuthDtos.LoginResponse issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("orqentra-auth")
                .subject(user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("restaurantRef", user.getRestaurant().getReference())
                .claim("role", user.getRole().name())
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new AuthDtos.LoginResponse(token, expiresAt);
    }
}
