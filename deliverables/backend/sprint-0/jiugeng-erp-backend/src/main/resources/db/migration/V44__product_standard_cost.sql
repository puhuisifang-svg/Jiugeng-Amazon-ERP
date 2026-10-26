ALTER TABLE product_auxiliary_material MODIFY standard_quantity DECIMAL(24,10) NOT NULL;
CREATE TABLE material_standard_cost (
 material_id VARCHAR(36) PRIMARY KEY,
 net_price DECIMAL(18,6) NOT NULL,
 unit VARCHAR(32) NOT NULL,
 source VARCHAR(16) NOT NULL,
 reference_id VARCHAR(36),
 reason VARCHAR(500) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_by VARCHAR(36) NOT NULL,
 CONSTRAINT fk_standard_cost_material FOREIGN KEY(material_id) REFERENCES auxiliary_material(id),
 CONSTRAINT ck_standard_cost_price CHECK(net_price>=0)
);
CREATE TABLE cost_operation (
 id VARCHAR(36) PRIMARY KEY,
 name VARCHAR(100) NOT NULL UNIQUE,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE sku_cost_state (
 variant_id VARCHAR(36) PRIMARY KEY,
 current_id VARCHAR(36),
 draft_id VARCHAR(36),
 sequence_number INT NOT NULL DEFAULT 0,
 CONSTRAINT fk_cost_state_sku FOREIGN KEY(variant_id) REFERENCES product_variant(id)
);
CREATE TABLE sku_cost_version (
 id VARCHAR(36) PRIMARY KEY,
 variant_id VARCHAR(36) NOT NULL,
 version_number INT NOT NULL,
 status VARCHAR(16) NOT NULL,
 input_json MEDIUMTEXT NOT NULL,
 snapshot_json MEDIUMTEXT NOT NULL,
 source_hash VARCHAR(64) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 reason VARCHAR(500) NOT NULL,
 created_by VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMP NULL,
 CONSTRAINT fk_cost_version_sku FOREIGN KEY(variant_id) REFERENCES product_variant(id),
 CONSTRAINT uq_cost_version UNIQUE(variant_id,version_number)
);
