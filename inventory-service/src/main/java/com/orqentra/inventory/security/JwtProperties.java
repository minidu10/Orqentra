package com.orqentra.inventory.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret shared HMAC secret used to verify tokens locally, from configuration
 */
@ConfigurationProperties(prefix = "orqentra.jwt")
public record JwtProperties(String secret) {}
