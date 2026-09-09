package com.orqentra.gateway.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param routes    path prefix to downstream base URL; the longest matching prefix wins
 * @param cors      browser access rules, configured here and nowhere else
 * @param rateLimit token bucket shape shared by every route
 */
@ConfigurationProperties(prefix = "orqentra.gateway")
public record GatewayProperties(
        Map<String, String> routes,
        Cors cors,
        RateLimit rateLimit) {

    public record Cors(List<String> allowedOrigins, Duration maxAge) {}

    public record RateLimit(int replenishRate, int burstCapacity) {}
}
