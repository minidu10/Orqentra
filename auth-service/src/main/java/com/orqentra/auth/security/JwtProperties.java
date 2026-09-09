package com.orqentra.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret shared HMAC secret, supplied by configuration rather than a literal
 * @param ttl    how long an issued token stays valid
 */
@ConfigurationProperties(prefix = "orqentra.jwt")
public record JwtProperties(String secret, Duration ttl) {}
