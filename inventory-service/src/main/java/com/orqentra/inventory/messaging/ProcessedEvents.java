package com.orqentra.inventory.messaging;

import org.springframework.stereotype.Component;

@Component
public class ProcessedEvents {

    private final ProcessedEventRepository repository;

    public ProcessedEvents(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Claims an event for a consumer. Returns false when it has already been handled, in
     * which case the caller must skip the work.
     *
     * <p>Must be called inside the same transaction as the business change, so the marker
     * and the state change commit together and a crash between them is impossible. The
     * read is only the fast path; the flush makes the composite primary key the real
     * guard, so a concurrent duplicate fails here instead of doing the work twice. That
     * failure dooms the transaction on purpose: Kafka redelivers, and the read then skips.
     */
    public boolean claim(String eventId, String consumer) {
        if (repository.existsById(new ProcessedEventId(eventId, consumer))) {
            return false;
        }
        repository.saveAndFlush(new ProcessedEvent(eventId, consumer));
        return true;
    }
}
