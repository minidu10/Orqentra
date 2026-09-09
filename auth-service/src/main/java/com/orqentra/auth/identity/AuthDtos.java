package com.orqentra.auth.identity;

import java.time.Instant;

public final class AuthDtos {

    public record RegisterRequest(String email, String password, String restaurantName) {}

    public record RegisterResponse(String email, String restaurantRef) {}

    public record LoginRequest(String email, String password) {}

    public record LoginResponse(String token, Instant expiresAt) {}

    public record MeResponse(String email, String restaurantRef, String role) {}

    private AuthDtos() {}
}
