CREATE TABLE processed_events (
    event_id      VARCHAR(36)  NOT NULL,
    consumer      VARCHAR(64)  NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Keyed on both columns so two listeners in the same service can each process the
    -- same event independently, while a redelivery to one listener is still rejected.
    PRIMARY KEY (event_id, consumer)
);

CREATE TABLE outbox_events (
    id            BIGSERIAL     PRIMARY KEY,
    event_id      VARCHAR(36)   NOT NULL UNIQUE,
    topic         VARCHAR(128)  NOT NULL,
    message_key   VARCHAR(128)  NOT NULL,
    payload       TEXT          NOT NULL,
    type_name     VARCHAR(64)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

-- The poller only ever reads unpublished rows, in id order.
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;
