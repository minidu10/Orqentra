package com.orqentra.gateway.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.orqentra.gateway.config.GatewayProperties;

import reactor.core.publisher.Mono;

/**
 * Token bucket held in Redis so the limit is shared across gateway instances rather than
 * counted per process. The check and the decrement run inside one Lua script, which Redis
 * executes atomically: doing it as separate GET and SET calls would let two concurrent
 * requests both read the same remaining count and both be allowed through.
 */
@Component
public class RedisRateLimiter {

    private static final String LUA = """
            local tokensKey = KEYS[1]
            local timestampKey = KEYS[2]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local lastTokens = tonumber(redis.call('get', tokensKey))
            if lastTokens == nil then
              lastTokens = capacity
            end

            local lastRefreshed = tonumber(redis.call('get', timestampKey))
            if lastRefreshed == nil then
              lastRefreshed = 0
            end

            local delta = math.max(0, now - lastRefreshed)
            local filled = math.min(capacity, lastTokens + (delta * rate))

            local allowed = 0
            local newTokens = filled
            if filled >= 1 then
              allowed = 1
              newTokens = filled - 1
            end

            local ttl = math.floor((capacity / rate) * 2) + 1
            redis.call('setex', tokensKey, ttl, newTokens)
            redis.call('setex', timestampKey, ttl, now)

            return allowed
            """;

    private final ReactiveStringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final GatewayProperties properties;

    public RedisRateLimiter(ReactiveStringRedisTemplate redis, GatewayProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.script = RedisScript.of(LUA, Long.class);
    }

    public Mono<Boolean> tryConsume(String key) {
        GatewayProperties.RateLimit limit = properties.rateLimit();
        List<String> keys = List.of("rl:{" + key + "}:tokens", "rl:{" + key + "}:ts");
        List<String> args = List.of(
                String.valueOf(limit.replenishRate()),
                String.valueOf(limit.burstCapacity()),
                String.valueOf(System.currentTimeMillis() / 1000.0));

        return redis.execute(script, keys, args)
                .singleOrEmpty()
                .map(allowed -> allowed != null && allowed == 1L)
                .defaultIfEmpty(true)
                // A Redis outage must not take the API down; the limiter fails open and
                // the request is allowed through.
                .onErrorReturn(true)
                .timeout(Duration.ofSeconds(2), Mono.just(true));
    }
}
