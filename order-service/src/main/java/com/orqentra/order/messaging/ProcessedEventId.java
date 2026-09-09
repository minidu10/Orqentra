package com.orqentra.order.messaging;

import java.io.Serializable;
import java.util.Objects;

/** Composite key: one row per (event, consumer) pair. */
public class ProcessedEventId implements Serializable {

    private String eventId;
    private String consumer;

    protected ProcessedEventId() {}

    public ProcessedEventId(String eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessedEventId that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId) && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumer);
    }
}
