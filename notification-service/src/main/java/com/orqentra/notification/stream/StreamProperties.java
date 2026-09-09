package com.orqentra.notification.stream;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param timeout           how long an idle SSE connection is held before the server ends it
 * @param heartbeatInterval how often a keep-alive comment is written to every emitter
 */
@ConfigurationProperties(prefix = "orqentra.stream")
public record StreamProperties(Duration timeout, Duration heartbeatInterval) {}
