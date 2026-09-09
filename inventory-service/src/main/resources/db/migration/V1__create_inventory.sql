CREATE TABLE inventory (
    id          BIGSERIAL   PRIMARY KEY,
    sku         VARCHAR(64) NOT NULL UNIQUE,
    available   INTEGER     NOT NULL CHECK (available >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- SKU-003 is deliberately low: it is the insufficient-stock case used in testing.
INSERT INTO inventory (sku, available) VALUES
    ('SKU-001', 100),
    ('SKU-002', 50),
    ('SKU-003', 8);
