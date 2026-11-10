
ALTER TABLE sample_location ADD COLUMN sample_image_id VARCHAR(36) NULL;
ALTER TABLE purchase_order DROP CONSTRAINT ck_purchase_order_type;
ALTER TABLE purchase_order ADD CONSTRAINT ck_purchase_order_type CHECK (order_type IN ('PRODUCT','MATERIAL','MANUAL'));
ALTER TABLE purchase_order MODIFY COLUMN supplier_id VARCHAR(36) NULL;
ALTER TABLE purchase_order ADD COLUMN supplier_name VARCHAR(200) NULL;
ALTER TABLE purchase_order_line ADD COLUMN specification VARCHAR(500) NULL;
ALTER TABLE purchase_order_line ADD COLUMN image_asset_id VARCHAR(36) NULL;
ALTER TABLE catalog_image_asset DROP CONSTRAINT ck_catalog_image_purpose;
ALTER TABLE catalog_image_asset ADD CONSTRAINT ck_catalog_image_purpose CHECK (purpose IN ('PRODUCT_MAIN','SKU_MAIN','SUPPLIER_QR','MATERIAL_IMAGE','SAMPLE_STORAGE','CUSTOMER_WECHAT','SAMPLE_IMAGE','PURCHASE_ITEM'));
