ALTER TABLE products ADD COLUMN stock_quantity BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN reserved_quantity BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN sale_status VARCHAR(32) NOT NULL DEFAULT 'STOPPED';
ALTER TABLE products ADD COLUMN stock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD CONSTRAINT chk_product_stock CHECK (stock_quantity >= 0 AND reserved_quantity >= 0);
ALTER TABLE orders ADD COLUMN stock_state VARCHAR(32) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE orders ADD COLUMN reservation_expires_at DATETIME(6);
ALTER TABLE orders ADD COLUMN restock_reason VARCHAR(500);
CREATE INDEX idx_order_inventory_expiry ON orders (status, reservation_expires_at);
