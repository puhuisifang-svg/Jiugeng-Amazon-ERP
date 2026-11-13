ALTER TABLE purchase_order_line ADD COLUMN tax_included BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE purchase_order_line ADD COLUMN tax_rate DECIMAL(5,2) NOT NULL DEFAULT 0;
ALTER TABLE purchase_order_line ADD CONSTRAINT ck_purchase_line_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100);
