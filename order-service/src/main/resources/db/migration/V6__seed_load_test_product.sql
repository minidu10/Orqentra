-- Matches the large stock seeded in inventory-service's V5 migration, so a load test can
-- place orders for this SKU without ever legitimately hitting the stock-rejection path.
INSERT INTO products (sku, name, price) VALUES
    ('SKU-LOAD-001', 'Load test item', 500.00)
ON CONFLICT (sku) DO NOTHING;
