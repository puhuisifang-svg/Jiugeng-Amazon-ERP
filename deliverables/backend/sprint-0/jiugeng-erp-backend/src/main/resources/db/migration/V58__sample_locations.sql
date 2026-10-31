CREATE TABLE sample_location (
 id VARCHAR(36) PRIMARY KEY,
 product_id VARCHAR(36) NOT NULL,
 variant_id VARCHAR(36) NOT NULL,
 product_name VARCHAR(200) NOT NULL,
 sku_code VARCHAR(64) NOT NULL,
 sku_name VARCHAR(200) NOT NULL,
 location_code VARCHAR(100) NOT NULL,
 location_normalized VARCHAR(100) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_sample_sku_location UNIQUE(variant_id,location_normalized)
);
CREATE INDEX idx_sample_location ON sample_location(location_normalized);
