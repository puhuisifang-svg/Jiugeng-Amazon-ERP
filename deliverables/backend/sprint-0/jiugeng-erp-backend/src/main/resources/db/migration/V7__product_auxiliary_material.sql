CREATE TABLE product_auxiliary_material (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NOT NULL,
    auxiliary_material_id VARCHAR(36) NOT NULL,
    standard_quantity DECIMAL(15, 4) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    loss_rate DECIMAL(7, 6) NOT NULL DEFAULT 0,
    remark VARCHAR(1000) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_material_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_material_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id),
    CONSTRAINT fk_product_material_material FOREIGN KEY (auxiliary_material_id) REFERENCES auxiliary_material (id),
    CONSTRAINT uk_product_material_variant_material UNIQUE (variant_id, auxiliary_material_id),
    CONSTRAINT ck_product_material_quantity CHECK (standard_quantity > 0),
    CONSTRAINT ck_product_material_loss_rate CHECK (loss_rate >= 0 AND loss_rate <= 1)
);

CREATE INDEX idx_product_material_product ON product_auxiliary_material (product_id);
CREATE INDEX idx_product_material_material ON product_auxiliary_material (auxiliary_material_id);
