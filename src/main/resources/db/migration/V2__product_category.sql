ALTER TABLE products ADD COLUMN category VARCHAR(80) NOT NULL DEFAULT 'Khác';
CREATE INDEX idx_products_category ON products(category);
