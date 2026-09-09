package com.orqentra.order.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

/**
 * Business outcomes, which is what anyone actually wants to know about this system.
 * Request rates and JVM memory say the process is alive; these say whether orders are
 * getting through, and how long a saga takes end to end.
 */
@Component
public class SagaMetrics {

    public static final String CONFIRMED = "confirmed";
    public static final String CANCELLED_STOCK = "cancelled_stock";
    public static final String CANCELLED_PAYMENT = "cancelled_payment";

    private final MeterRegistry registry;

    /**
     * Start times for sagas still in flight. Bounded in practice by the number of open
     * orders; an entry is removed the moment the saga reaches a terminal state.
     */
    private final Map<String, Instant> started = new ConcurrentHashMap<>();

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void sagaStarted(String orderReference) {
        started.put(orderReference, Instant.now());
    }

    public void sagaFinished(String orderReference, String result) {
        registry.counter("orqentra.saga.outcome", "result", result).increment();

        Instant begin = started.remove(orderReference);
        if (begin == null) {
            // Started before this process did, so the duration is unknown. The outcome
            // still counts; inventing a duration would be worse than omitting one.
            return;
        }

        Timer.builder("orqentra.saga.duration")
                .description("Order accepted until the saga reached a terminal state")
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.between(begin, Instant.now()));
    }

    public int inFlight() {
        return started.size();
    }
}
