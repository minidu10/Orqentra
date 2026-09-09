package com.orqentra.payment.messaging;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Id
    @Column(nullable = false)
    private String consumer;

    @Column(name = "processed_at", insertable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(String eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
    }

    public String getEventId() { return eventId; }
    public String getConsumer() { return consumer; }
    public Instant getProcessedAt() { return processedAt; }
}
