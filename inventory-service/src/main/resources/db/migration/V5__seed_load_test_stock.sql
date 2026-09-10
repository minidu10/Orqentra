-- A dedicated SKU with enough stock that a sustained load test never legitimately hits
-- the insufficient-stock rejection path. Mixing that path into a load test would mean
-- the measured latency and error rate reflect two different behaviours (a normal
-- reservation vs. a rejection-and-compensation saga) rather than one.
INSERT INTO inventory (sku, available) VALUES ('SKU-LOAD-001', 1000000)
ON CONFLICT (sku) DO UPDATE SET available = EXCLUDED.available;
