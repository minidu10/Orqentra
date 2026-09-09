CREATE TABLE products (
    id          BIGSERIAL      PRIMARY KEY,
    sku         VARCHAR(64)    NOT NULL UNIQUE,
    name        VARCHAR(255)   NOT NULL,
    price       NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
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

INSERT INTO products (sku, name, price) VALUES
    ('SKU-001', 'Basmati rice 25kg',  12500.00),
    ('SKU-002', 'Sunflower oil 5L',    4200.00),
    ('SKU-003', 'Chicken breast 1kg',  1850.00);
