CREATE TABLE dashboard_shipment_document (
    id VARCHAR(36) PRIMARY KEY,
    today_shipment_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    byte_size BIGINT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_shipment_document_kind CHECK (kind IN ('LABEL','CARTON_LABEL'))
);
CREATE INDEX ix_shipment_document_owner ON dashboard_shipment_document(today_shipment_id, product_id, kind);
