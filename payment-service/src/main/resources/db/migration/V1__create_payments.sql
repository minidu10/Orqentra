CREATE TABLE payments (
    id               BIGSERIAL      PRIMARY KEY,
    order_reference  VARCHAR(36)    NOT NULL UNIQUE,
    amount           NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(32)    NOT NULL,
    failure_reason   VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
