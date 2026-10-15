ALTER TABLE auxiliary_material ADD COLUMN image_asset_id VARCHAR(36) NULL;

ALTER TABLE catalog_image_asset DROP CONSTRAINT ck_catalog_image_purpose;
ALTER TABLE catalog_image_asset ADD CONSTRAINT ck_catalog_image_purpose
    CHECK (purpose IN ('SKU_MAIN', 'SUPPLIER_QR', 'MATERIAL_IMAGE'));
