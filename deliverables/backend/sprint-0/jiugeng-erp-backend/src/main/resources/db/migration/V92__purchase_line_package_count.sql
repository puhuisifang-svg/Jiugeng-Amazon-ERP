ALTER TABLE purchase_order_line ADD COLUMN package_count INT NULL;
ALTER TABLE purchase_order_line ADD CONSTRAINT ck_purchase_package_count CHECK (package_count >= 0);
