CREATE TABLE catalog_image_variant (
 asset_id VARCHAR(36) PRIMARY KEY,
 storage_key VARCHAR(255), media_type VARCHAR(64), etag VARCHAR(64),
 next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_image_variant_asset FOREIGN KEY (asset_id) REFERENCES catalog_image_asset(id) ON DELETE CASCADE
);
CREATE TABLE catalog_external_image (
 id VARCHAR(64) PRIMARY KEY, created_by VARCHAR(36), lossless BOOLEAN NOT NULL DEFAULT FALSE, source_url VARCHAR(2048) NOT NULL,
 original_key VARCHAR(255), thumbnail_key VARCHAR(255),
 original_media_type VARCHAR(64), thumbnail_media_type VARCHAR(64),
 content_sha VARCHAR(64), source_etag VARCHAR(512), source_modified VARCHAR(128),
 checked_at TIMESTAMP, next_check_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 failure_count INT NOT NULL DEFAULT 0,
 lease_until TIMESTAMP, last_accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_external_image_due ON catalog_external_image(next_check_at);
CREATE TABLE catalog_image_retired_file (
 storage_key VARCHAR(255) PRIMARY KEY, delete_after TIMESTAMP NOT NULL
);
CREATE TABLE catalog_external_image_grant (
 image_id VARCHAR(64) NOT NULL, actor_id VARCHAR(36) NOT NULL, permission VARCHAR(80) NOT NULL,
 PRIMARY KEY(image_id,actor_id,permission),
 CONSTRAINT fk_external_image_grant FOREIGN KEY(image_id) REFERENCES catalog_external_image(id) ON DELETE CASCADE
);
