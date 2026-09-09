package com.orqentra.gateway.support;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Writes a ProblemDetail body directly. These responses are produced inside filters,
 * before any message converter is involved, so the JSON is built by hand to keep the
 * error shape identical to the one the downstream services return.
 */
public final class ProblemResponse {

    public static Mono<Void> write(ServerWebExchange exchange,
                                   HttpStatus status,
                                   String title,
                                   String detail) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String path = exchange.getRequest().getPath().value();
        String body = "{\"type\":\"about:blank\",\"title\":\"" + escape(title)
                + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + escape(detail)
                + "\",\"instance\":\"" + escape(path) + "\"}";

        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ProblemResponse() {}
}
