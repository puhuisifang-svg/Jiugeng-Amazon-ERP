-- Existing parent-level records deliberately remain unbound: never guess a SKU.
ALTER TABLE dashboard_today_shipment ADD COLUMN variant_id VARCHAR(36);
ALTER TABLE dashboard_pending_shipment ADD COLUMN variant_id VARCHAR(36);
ALTER TABLE dashboard_shipment_dispatch ADD COLUMN variant_id VARCHAR(36);
ALTER TABLE dashboard_shipment_document ADD COLUMN variant_id VARCHAR(36);
ALTER TABLE dashboard_today_shipment MODIFY COLUMN product_name_snapshot VARCHAR(200) NOT NULL;
ALTER TABLE dashboard_pending_shipment MODIFY COLUMN product_name_snapshot VARCHAR(200) NOT NULL;
ALTER TABLE dashboard_shipment_dispatch MODIFY COLUMN product_name VARCHAR(200) NOT NULL;
