ALTER TABLE sample_location ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE sample_location ADD COLUMN deleted_by VARCHAR(36) NULL;
ALTER TABLE sample_location ADD COLUMN deletion_key VARCHAR(36) NOT NULL DEFAULT '';
ALTER TABLE sample_location ADD COLUMN snapshot_image_url VARCHAR(2000) NULL;
ALTER TABLE sample_location ADD COLUMN image_snapshot_taken BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sample_location DROP INDEX uk_sample_sku_location;
ALTER TABLE sample_location ADD CONSTRAINT uk_sample_sku_location UNIQUE(variant_id,location_normalized,deletion_key);
CREATE INDEX idx_sample_page ON sample_location(deletion_key,updated_at,id);
CREATE INDEX idx_sample_parent_page ON sample_location(deletion_key,product_id,updated_at,id);
CREATE INDEX idx_sample_shelf_page ON sample_location(deletion_key,location_normalized,updated_at,id);
CREATE TABLE sample_write_request (
 actor_id VARCHAR(36) NOT NULL,
 request_id VARCHAR(100) NOT NULL,
 operation VARCHAR(50) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 sample_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(actor_id,request_id)
);
