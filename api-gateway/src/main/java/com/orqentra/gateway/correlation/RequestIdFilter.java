package com.orqentra.gateway.correlation;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Establishes the request id for the whole hop: reuses an inbound X-Request-Id so a
 * client can correlate its own call, or mints one. Runs first so every later filter,
 * including the rejections, logs under the same id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "orqentra.requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        exchange.getAttributes().put(ATTRIBUTE, requestId);
        // Echoed back so a client can quote the id when reporting a problem.
        exchange.getResponse().getHeaders().set(HEADER, requestId);

        // Logged into the message rather than the MDC. Without Reactor context
        // propagation on the classpath, an MDC value set here does not survive the
        // operator and thread hops of the reactive chain, so it would silently read as
        // absent on the very lines that matter. The servlet services do use the MDC,
        // where it is bound to the request thread and does hold.
        log.info("[{}] {} {}", requestId,
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value());

        return chain.filter(exchange);
    }
}
