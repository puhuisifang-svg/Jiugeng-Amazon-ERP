CREATE TABLE product (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL,
    product_code_normalized VARCHAR(64) NOT NULL,
    name_zh VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NULL,
    brand VARCHAR(100) NULL,
    internal_category VARCHAR(100) NULL,
    product_type VARCHAR(100) NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_code_normalized UNIQUE (product_code_normalized),
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED'))
);

CREATE TABLE product_variant (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_code_normalized VARCHAR(64) NOT NULL,
    variant_name VARCHAR(200) NOT NULL,
    barcode VARCHAR(64) NULL,
    barcode_normalized VARCHAR(64) NULL,
    material VARCHAR(200) NULL,
    specification VARCHAR(300) NULL,
    item_dimensions VARCHAR(200) NULL,
    package_dimensions VARCHAR(200) NULL,
    package_weight DECIMAL(15, 4) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    attribute_signature VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_variant_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT uk_product_variant_sku_normalized UNIQUE (sku_code_normalized),
    CONSTRAINT uk_product_variant_barcode_normalized UNIQUE (barcode_normalized),
    CONSTRAINT uk_product_variant_attribute_signature UNIQUE (product_id, attribute_signature),
    CONSTRAINT ck_product_variant_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_product_variant_package_weight CHECK (package_weight IS NULL OR package_weight >= 0)
);

CREATE TABLE variant_attribute (
    variant_id VARCHAR(36) NOT NULL,
    attribute_code VARCHAR(64) NOT NULL,
    attribute_code_normalized VARCHAR(64) NOT NULL,
    attribute_value VARCHAR(300) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (variant_id, attribute_code_normalized),
    CONSTRAINT fk_variant_attribute_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id) ON DELETE CASCADE,
    CONSTRAINT ck_variant_attribute_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_product_status_code ON product (status, product_code_normalized);
CREATE INDEX idx_product_variant_product ON product_variant (product_id);
