CREATE TABLE products (
    id          BIGSERIAL      PRIMARY KEY,
    sku         VARCHAR(64)    NOT NULL UNIQUE,
    name        VARCHAR(255)   NOT NULL,
    price       NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

INSERT INTO products (sku, name, price) VALUES
    ('SKU-001', 'Basmati rice 25kg',  12500.00),
    ('SKU-002', 'Sunflower oil 5L',    4200.00),
    ('SKU-003', 'Chicken breast 1kg',  1850.00);