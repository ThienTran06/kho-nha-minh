ALTER TABLE products ADD COLUMN package_unit VARCHAR(60);
ALTER TABLE products ADD COLUMN units_per_package DECIMAL(19,3) NOT NULL DEFAULT 0;
ALTER TABLE inventory_transactions ADD COLUMN entered_quantity DECIMAL(19,3);
ALTER TABLE inventory_transactions ADD COLUMN entered_unit VARCHAR(60);
ALTER TABLE inventory_transactions ADD COLUMN conversion_factor DECIMAL(19,3);
UPDATE inventory_transactions SET entered_quantity = quantity, entered_unit = unit_snapshot, conversion_factor = 1 WHERE entered_quantity IS NULL;
