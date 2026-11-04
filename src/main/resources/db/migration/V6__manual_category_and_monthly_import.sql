ALTER TABLE products ADD COLUMN category_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_transactions ADD COLUMN source_key VARCHAR(220);
CREATE UNIQUE INDEX ux_tx_source_key ON inventory_transactions(source_key);
