ALTER TABLE inventory_transactions ADD COLUMN supplier VARCHAR(180);
ALTER TABLE inventory_transactions ADD COLUMN receiver_name VARCHAR(180);
ALTER TABLE inventory_transactions ADD COLUMN invoice_sent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_transactions ADD COLUMN department VARCHAR(180);
ALTER TABLE inventory_transactions ADD COLUMN employee_name VARCHAR(180);
