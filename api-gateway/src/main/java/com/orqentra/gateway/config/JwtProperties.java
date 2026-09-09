package com.orqentra.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orqentra.jwt")
public record JwtProperties(String secret) {}
