CREATE TABLE sku_document (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    variant_id VARCHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    byte_size BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sku_document_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id) ON DELETE CASCADE,
    CONSTRAINT ck_sku_document_kind CHECK (kind IN ('MANUAL', 'LABEL'))
);
CREATE INDEX idx_sku_document_variant_kind ON sku_document(variant_id, kind);
