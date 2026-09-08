CREATE TABLE inventory (
    id          BIGSERIAL   PRIMARY KEY,
    sku         VARCHAR(64) NOT NULL UNIQUE,
    available   INTEGER     NOT NULL CHECK (available >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id             BIGSERIAL      PRIMARY KEY,
    reference      VARCHAR(36)    NOT NULL UNIQUE,
    restaurant_id  VARCHAR(64)    NOT NULL,
    status         VARCHAR(32)    NOT NULL,
    total          NUMERIC(12, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id          BIGSERIAL      PRIMARY KEY,
    order_id    BIGINT         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku         VARCHAR(64)    NOT NULL,
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

INSERT INTO inventory (sku, available) VALUES
    ('SKU-001', 100),
    ('SKU-002', 50),
    ('SKU-003', 8);