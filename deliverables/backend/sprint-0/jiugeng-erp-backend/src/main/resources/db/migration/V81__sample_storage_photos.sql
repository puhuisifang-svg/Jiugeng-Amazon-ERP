ALTER TABLE sample_location MODIFY COLUMN product_id VARCHAR(36) NULL;
ALTER TABLE sample_location MODIFY COLUMN variant_id VARCHAR(36) NULL;
ALTER TABLE sample_location ADD COLUMN storage_image_id VARCHAR(36) NULL;
ALTER TABLE sample_location ADD COLUMN specification VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE sample_location ADD COLUMN remark VARCHAR(1000) NOT NULL DEFAULT '';
CREATE INDEX idx_sample_product ON sample_location(product_id);
ALTER TABLE catalog_image_asset DROP CONSTRAINT ck_catalog_image_purpose;
ALTER TABLE catalog_image_asset ADD CONSTRAINT ck_catalog_image_purpose CHECK (purpose IN ('PRODUCT_MAIN','SKU_MAIN','SUPPLIER_QR','MATERIAL_IMAGE','SAMPLE_STORAGE'));
