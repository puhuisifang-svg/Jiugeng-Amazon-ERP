ALTER TABLE product ADD COLUMN main_image_asset_id VARCHAR(36);
ALTER TABLE catalog_image_asset DROP CONSTRAINT ck_catalog_image_purpose;
ALTER TABLE catalog_image_asset ADD CONSTRAINT ck_catalog_image_purpose
    CHECK (purpose IN ('PRODUCT_MAIN', 'SKU_MAIN', 'SUPPLIER_QR', 'MATERIAL_IMAGE'));
ALTER TABLE product ADD CONSTRAINT fk_product_main_image
    FOREIGN KEY (main_image_asset_id) REFERENCES catalog_image_asset(id);
