CREATE TABLE carton_label_template (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    config TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_carton_template_customer FOREIGN KEY(customer_id) REFERENCES customer(id)
);
CREATE INDEX idx_carton_template_customer ON carton_label_template(customer_id);
CREATE TABLE carton_label_export (
    id VARCHAR(36) PRIMARY KEY,
    request_key VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    page_count INT NOT NULL,
    content LONGBLOB NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_carton_export_request UNIQUE(created_by,request_key)
);
CREATE TABLE carton_label_export_item (
    export_id VARCHAR(36) NOT NULL,
    shipment_id VARCHAR(36) NOT NULL,
    snapshot TEXT NOT NULL,
    PRIMARY KEY(export_id,shipment_id),
    CONSTRAINT fk_carton_export_item FOREIGN KEY(export_id) REFERENCES carton_label_export(id)
);
CREATE INDEX idx_carton_export_shipment ON carton_label_export_item(shipment_id);
