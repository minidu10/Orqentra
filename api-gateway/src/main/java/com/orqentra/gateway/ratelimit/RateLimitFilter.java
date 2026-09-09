package com.orqentra.gateway.ratelimit;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.orqentra.gateway.support.ProblemResponse;

import reactor.core.publisher.Mono;

/**
 * Runs after authentication so an authenticated caller can be keyed on identity rather
 * than address. Unauthenticated routes fall back to client IP, which is the point for
 * /api/auth: login is the endpoint an attacker hammers to guess passwords, and it is
 * exactly the route where there is no identity to key on yet.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RateLimitFilter implements WebFilter {

    private final RedisRateLimiter limiter;

    public RateLimitFilter(RedisRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() == org.springframework.http.HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // An SSE subscribe is one request that then stays open for as long as the client
        // is connected. The filter only runs on the initial request, so it could never
        // drain the bucket continuously, but charging it a token at all means a client
        // reconnecting a dropped stream eats into the budget for its ordinary API calls.
        // Streams are exempt; they are bounded by the emitter count, not the rate limit.
        if (exchange.getRequest().getPath().value().startsWith("/api/notifications/stream")) {
            return chain.filter(exchange);
        }

        return resolveKey(exchange)
                .flatMap(key -> limiter.tryConsume(key)
                        .flatMap(allowed -> allowed
                                ? chain.filter(exchange)
                                : ProblemResponse.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                                        "Too many requests",
                                        "Rate limit exceeded, please retry shortly")));
    }

    private Mono<String> resolveKey(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .map(authentication -> authentication.getPrincipal() instanceof Jwt jwt
                        ? "user:" + jwt.getSubject()
                        : "user:" + authentication.getName())
                .defaultIfEmpty("ip:" + clientIp(exchange));
    }

    private String clientIp(ServerWebExchange exchange) {
        List<String> forwarded = exchange.getRequest().getHeaders().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.get(0).split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }
}
