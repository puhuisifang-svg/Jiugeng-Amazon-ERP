ALTER TABLE purchase_order ADD COLUMN expected_delivery_date DATE;
ALTER TABLE purchase_order ADD COLUMN delivery_address VARCHAR(500);
ALTER TABLE purchase_order ADD COLUMN cancellation_reason VARCHAR(500);
CREATE INDEX idx_purchase_order_type_delivery ON purchase_order(order_type, expected_delivery_date);
