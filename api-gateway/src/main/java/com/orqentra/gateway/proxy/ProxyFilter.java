package com.orqentra.gateway.proxy;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.observation.ObservationRegistry;

import com.orqentra.gateway.correlation.RequestIdFilter;
import com.orqentra.gateway.support.ProblemResponse;

import reactor.core.publisher.Mono;

/**
 * Forwards the request to the service that owns the path and streams the reply straight
 * back.
 *
 * <p>Written as a filter rather than a handler function on purpose: building a
 * ServerResponse around the client's body publisher releases the client response before
 * anything subscribes to it, so the status and headers arrive but the body is empty.
 * Writing into the exchange response keeps the upstream connection open until the bytes
 * have actually been copied.
 *
 * <p>Runs last, so a request only reaches a service after the correlation id is set, the
 * token has been verified, and the rate limit has been checked.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProxyFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ProxyFilter.class);

    /**
     * Hop-by-hop headers describe this connection rather than the message, so they must
     * not be passed on. Content-Length goes too, because the forwarded body is streamed.
     */
    private static final Set<String> STRIPPED = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length", "host");

    private final WebClient webClient;
    private final ProxyRoutes routes;

    public ProxyFilter(ProxyRoutes routes, ObservationRegistry observationRegistry) {
        // Built directly: Boot 4 does not auto-configure a WebClient.Builder bean.
        //
        // The observation registry has to be attached by hand for the same reason. An
        // auto-configured builder would carry it already; this one would not, and the
        // outgoing call would then carry no traceparent header. Every downstream service
        // would start its own trace and the gateway hop would be missing from all of
        // them, which is the single easiest way to end up with a trace that looks
        // plausible and is quietly wrong.
        this.webClient = WebClient.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .observationRegistry(observationRegistry)
                .build();
        this.routes = routes;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        Optional<String> target = routes.targetFor(path);

        if (target.isEmpty()) {
            return chain.filter(exchange);
        }

        String requestId = (String) exchange.getAttributes()
                .getOrDefault(RequestIdFilter.ATTRIBUTE, "");

        String query = exchange.getRequest().getURI().getRawQuery();
        URI uri = URI.create(target.get() + path + (query == null ? "" : "?" + query));

        log.info("[{}] {} {} -> {}", requestId, exchange.getRequest().getMethod(), path, uri);

        return webClient.method(exchange.getRequest().getMethod())
                .uri(uri)
                .headers(headers -> {
                    exchange.getRequest().getHeaders().forEach((name, values) -> {
                        if (!STRIPPED.contains(name.toLowerCase())) {
                            headers.addAll(name, values);
                        }
                    });
                    // The original Authorization header travels on untouched. It is never
                    // swapped for trusted headers such as X-Restaurant-Ref: if it were,
                    // anything able to reach a service directly could forge them and the
                    // services would have no defence of their own.
                    headers.set(RequestIdFilter.HEADER, requestId);
                })
                .body(BodyInserters.fromDataBuffers(exchange.getRequest().getBody()))
                .exchangeToMono(clientResponse -> {
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(clientResponse.statusCode());

                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!STRIPPED.contains(name.toLowerCase())
                                && !response.getHeaders().containsHeader(name)) {
                            response.getHeaders().addAll(name, values);
                        }
                    });

                    return response.writeWith(clientResponse.bodyToFlux(DataBuffer.class));
                })
                .onErrorResume(error -> {
                    log.warn("[{}] proxying {} failed: {}", requestId, uri, error.toString());
                    return ProblemResponse.write(exchange, HttpStatus.BAD_GATEWAY,
                            "Upstream unavailable",
                            "The service handling this request is not reachable");
                });
    }
}
