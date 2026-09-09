package com.orqentra.inventory.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.stereotype.Component;

/**
 * How many outbox rows are still unpublished, right now.
 *
 * <p>A number that stays near zero means the poller is keeping up. A number that climbs
 * means events are being written but not delivered, which the rest of the system will not
 * notice on its own: orders are accepted, nothing downstream ever happens, and no error
 * is logged anywhere.
 */
@Component
public class OutboxDepthMetrics implements MeterBinder {

    private final OutboxEventRepository repository;

    public OutboxDepthMetrics(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge("orqentra.outbox.pending", this, OutboxDepthMetrics::pending);
    }

    private double pending() {
        try {
            return repository.countByPublishedAtIsNull();
        } catch (Exception ex) {
            // A gauge must never throw into the scrape path.
            return Double.NaN;
        }
    }
}
