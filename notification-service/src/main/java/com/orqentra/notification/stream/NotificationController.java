package com.orqentra.notification.stream;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final EmitterRegistry registry;
    private final OrderOwnershipView ownership;
    private final StreamProperties properties;

    public NotificationController(EmitterRegistry registry,
                                  OrderOwnershipView ownership,
                                  StreamProperties properties) {
        this.registry = registry;
        this.ownership = ownership;
        this.properties = properties;
    }

    /**
     * The subscription key comes from the verified token and nowhere else. Taking it from
     * a query parameter would let any authenticated caller subscribe to another
     * restaurant's order flow simply by changing the URL.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
        String key = admin ? EmitterRegistry.ALL : jwt.getClaimAsString("restaurantRef");

        SseEmitter emitter = registry.register(key, properties.timeout().toMillis());

        try {
            // Something must be written straight away. With nothing flushed, proxies and
            // clients hold the response open and the stream looks dead until the first
            // real event, which may be minutes away.
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "subscribedAs", jwt.getSubject(),
                            "scope", admin ? "all restaurants" : key)));
        } catch (IOException ex) {
            log.info("Client disconnected before the connected event could be written");
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    /** Small diagnostics view, used to show emitters being deregistered on disconnect. */
    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return Map.of(
                "openConnections", registry.total(),
                "keys", registry.keyCount(),
                "knownOrders", ownership.size());
    }
}
