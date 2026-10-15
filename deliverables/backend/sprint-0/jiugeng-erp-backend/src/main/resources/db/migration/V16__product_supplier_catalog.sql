ALTER TABLE product ADD COLUMN product_name VARCHAR(200) NULL;
UPDATE product
SET product_name = COALESCE(NULLIF(TRIM(name_zh), ''), NULLIF(TRIM(name_en), ''));
ALTER TABLE product ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE product_variant ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
UPDATE product_variant SET lifecycle_status = status;
ALTER TABLE product_variant ADD COLUMN package_weight_grams DECIMAL(15, 3) NULL;
UPDATE product_variant
SET package_weight_grams = CASE package_weight_unit
    WHEN 'G' THEN ROUND(package_weight, 3)
    WHEN 'KG' THEN ROUND(package_weight * 1000, 3)
    WHEN 'LB' THEN ROUND(package_weight * 453.59237, 3)
    WHEN 'OZ' THEN ROUND(package_weight * 28.349523125, 3)
    ELSE NULL
END
WHERE package_weight IS NOT NULL;
ALTER TABLE product_variant ADD COLUMN main_image_asset_id VARCHAR(36) NULL;
ALTER TABLE product_variant ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE product_migration_issue (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    issue_code VARCHAR(64) NOT NULL,
    raw_value VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO product_migration_issue (id, resource_type, resource_id, field_name, issue_code, raw_value)
SELECT CONCAT('weight-', SUBSTRING(id, 1, 29)), 'PRODUCT_VARIANT', id, 'package_weight',
       'WEIGHT_CONVERSION_FAILED', CONCAT(COALESCE(CAST(package_weight AS CHAR), ''), ' ', COALESCE(package_weight_unit, ''))
FROM product_variant
WHERE package_weight IS NOT NULL AND package_weight_grams IS NULL;

CREATE TABLE supplier (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    contact_person VARCHAR(64) NULL,
    phone VARCHAR(32) NULL,
    source_url VARCHAR(500) NULL,
    qr_image_asset_id VARCHAR(36) NULL,
    remark VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_supplier_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_supplier_contact CHECK (phone IS NOT NULL OR source_url IS NOT NULL OR qr_image_asset_id IS NOT NULL)
);

CREATE INDEX idx_supplier_status_name ON supplier (status, name);

CREATE TABLE product_variant_supplier (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    variant_id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    purchase_url VARCHAR(500) NULL,
    remark VARCHAR(500) NULL,
    primary_slot VARCHAR(16) NULL,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_variant_supplier_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id),
    CONSTRAINT fk_variant_supplier_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT uk_variant_supplier UNIQUE (variant_id, supplier_id),
    CONSTRAINT uk_variant_primary_supplier UNIQUE (variant_id, primary_slot),
    CONSTRAINT ck_variant_primary_slot CHECK (primary_slot IS NULL OR primary_slot = 'PRIMARY')
);

CREATE TABLE auxiliary_material_supplier (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    auxiliary_material_id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    purchase_url VARCHAR(500) NULL,
    remark VARCHAR(500) NULL,
    primary_slot VARCHAR(16) NULL,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_material_supplier_material FOREIGN KEY (auxiliary_material_id) REFERENCES auxiliary_material (id),
    CONSTRAINT fk_material_supplier_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT uk_material_supplier UNIQUE (auxiliary_material_id, supplier_id),
    CONSTRAINT uk_material_primary_supplier UNIQUE (auxiliary_material_id, primary_slot),
    CONSTRAINT ck_material_primary_slot CHECK (primary_slot IS NULL OR primary_slot = 'PRIMARY')
);

CREATE TABLE catalog_image_asset (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    original_storage_key VARCHAR(100) NOT NULL,
    thumbnail_storage_key VARCHAR(100) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    source_media_type VARCHAR(32) NOT NULL,
    stored_media_type VARCHAR(32) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    pixel_width INTEGER NOT NULL,
    pixel_height INTEGER NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    attached_resource_type VARCHAR(32) NULL,
    attached_resource_id VARCHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attached_at TIMESTAMP NULL,
    CONSTRAINT uk_catalog_image_original_key UNIQUE (original_storage_key),
    CONSTRAINT uk_catalog_image_thumbnail_key UNIQUE (thumbnail_storage_key),
    CONSTRAINT ck_catalog_image_purpose CHECK (purpose IN ('SKU_MAIN', 'SUPPLIER_QR')),
    CONSTRAINT ck_catalog_image_status CHECK (status IN ('PENDING', 'ACTIVE')),
    CONSTRAINT ck_catalog_image_size CHECK (byte_size > 0 AND byte_size <= 5242880),
    CONSTRAINT ck_catalog_image_dimensions CHECK (
        pixel_width > 0 AND pixel_width <= 4096 AND pixel_height > 0 AND pixel_height <= 4096
        AND pixel_width * pixel_height <= 16000000)
);

CREATE INDEX idx_catalog_image_pending_time ON catalog_image_asset (status, created_at);
CREATE INDEX idx_variant_supplier_supplier ON product_variant_supplier (supplier_id);
CREATE INDEX idx_material_supplier_supplier ON auxiliary_material_supplier (supplier_id);
