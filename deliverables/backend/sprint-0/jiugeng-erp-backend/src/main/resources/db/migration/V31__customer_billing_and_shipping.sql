ALTER TABLE customer ADD COLUMN invoice_title VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE customer ADD COLUMN tax_number VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE customer ADD COLUMN registered_address VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE customer ADD COLUMN registered_phone VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE customer ADD COLUMN bank_name VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE customer ADD COLUMN bank_account VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE customer_product MODIFY COLUMN supply_price DECIMAL(14,2) NULL;
CREATE TABLE customer_preferences (
    id INTEGER NOT NULL PRIMARY KEY,
    default_customer_id VARCHAR(36) NULL,
    CONSTRAINT ck_customer_preferences_singleton CHECK (id=1),
    CONSTRAINT fk_default_customer FOREIGN KEY (default_customer_id) REFERENCES customer(id)
);
INSERT INTO customer_preferences(id) VALUES(1);
ALTER TABLE dashboard_today_shipment ADD COLUMN customer_product_id VARCHAR(36) NULL;
ALTER TABLE dashboard_today_shipment ADD COLUMN customer_snapshot TEXT NULL;
ALTER TABLE dashboard_pending_shipment ADD COLUMN customer_product_id VARCHAR(36) NULL;
ALTER TABLE dashboard_pending_shipment ADD COLUMN customer_snapshot TEXT NULL;
ALTER TABLE dashboard_shipment_dispatch ADD COLUMN customer_product_id VARCHAR(36) NULL;
ALTER TABLE dashboard_shipment_dispatch ADD COLUMN customer_snapshot TEXT NULL;
ALTER TABLE dashboard_shipment_document ADD COLUMN customer_product_id VARCHAR(36) NULL;
CREATE INDEX idx_today_customer_product ON dashboard_today_shipment(customer_product_id);
CREATE INDEX idx_pending_customer_product ON dashboard_pending_shipment(customer_product_id);
CREATE INDEX idx_dispatch_customer_product ON dashboard_shipment_dispatch(customer_product_id);
