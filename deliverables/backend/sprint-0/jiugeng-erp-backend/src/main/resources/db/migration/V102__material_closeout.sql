ALTER TABLE auxiliary_material ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE material_write_request (
 actor_id VARCHAR(36) NOT NULL,
 request_id VARCHAR(100) NOT NULL,
 operation VARCHAR(100) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 material_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(actor_id,request_id)
);
CREATE INDEX idx_material_type_page ON auxiliary_material(material_type,status,material_code_normalized);
CREATE INDEX idx_material_supplier_owner ON auxiliary_material_supplier(auxiliary_material_id,supplier_id);
