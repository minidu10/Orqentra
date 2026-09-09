package com.orqentra.notification.stream;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.stereotype.Component;

/** How many browsers are currently attached to the live stream. */
@Component
public class StreamMetrics implements MeterBinder {

    private final EmitterRegistry registry;

    public StreamMetrics(EmitterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void bindTo(MeterRegistry meters) {
        meters.gauge("orqentra.sse.connections", registry, EmitterRegistry::total);
        meters.gauge("orqentra.sse.known.orders", registry, r -> r.keyCount());
    }
}
