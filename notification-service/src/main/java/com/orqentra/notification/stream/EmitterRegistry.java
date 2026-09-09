package com.orqentra.notification.stream;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds the open SSE connections, keyed by restaurant reference. A restaurant may have
 * several at once — two browser tabs are two emitters — so each key maps to a set.
 */
@Component
public class EmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(EmitterRegistry.class);

    /** Admin subscribers see every restaurant's events, so they live under one key. */
    public static final String ALL = "*";

    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String key, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);

        emitters.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        // Without all three callbacks the map grows for the life of the process: every
        // closed tab, timed-out connection and broken pipe would leave an entry behind.
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(error -> remove(key, emitter));

        log.info("Registered stream for {} ({} open for this key)", key, count(key));
        return emitter;
    }

    /**
     * Pushes to the restaurant's own connections and to any admin listeners.
     */
    public void send(String restaurantRef, String eventName, Object payload) {
        deliver(restaurantRef, eventName, payload);
        if (!ALL.equals(restaurantRef)) {
            deliver(ALL, eventName, payload);
        }
    }

    private void deliver(String key, String eventName, Object payload) {
        Set<SseEmitter> targets = emitters.get(key);
        if (targets == null || targets.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                // The client is gone. Writing to a dead emitter throws, so it is caught
                // and deregistered here rather than being allowed to escape into the
                // Kafka listener and fail the whole record.
                remove(key, emitter);
            }
        }
    }

    /**
     * Comment lines, not events, so a client never sees them as data. Intermediaries drop
     * connections that go quiet, and without this the client would only discover the
     * connection was dead when an event failed to turn up.
     */
    @Scheduled(fixedDelayString = "${orqentra.stream.heartbeat-interval:PT20S}")
    public void heartbeat() {
        emitters.forEach((key, targets) -> {
            for (SseEmitter emitter : targets) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException ex) {
                    remove(key, emitter);
                }
            }
        });
    }

    private void remove(String key, SseEmitter emitter) {
        Set<SseEmitter> targets = emitters.get(key);
        if (targets != null) {
            targets.remove(emitter);
            if (targets.isEmpty()) {
                emitters.remove(key, targets);
            }
        }
        log.info("Deregistered stream for {} ({} open for this key)", key, count(key));
    }

    public int count(String key) {
        Set<SseEmitter> targets = emitters.get(key);
        return targets == null ? 0 : targets.size();
    }

    /** Total open connections, used by the diagnostics endpoint. */
    public int total() {
        return emitters.values().stream().mapToInt(Set::size).sum();
    }

    public int keyCount() {
        return emitters.size();
    }
}
