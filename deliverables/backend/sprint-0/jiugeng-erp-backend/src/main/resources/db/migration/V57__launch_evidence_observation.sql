CREATE TABLE launch_observation (
 entity_id VARCHAR(36) PRIMARY KEY, observation_json MEDIUMTEXT NOT NULL,
 observed_at TIMESTAMP NOT NULL, provider VARCHAR(100) NOT NULL
);
CREATE TABLE launch_attachment (
 id VARCHAR(36) PRIMARY KEY, kind VARCHAR(16) NOT NULL, entity_id VARCHAR(36) NOT NULL,
 file_name VARCHAR(200) NOT NULL, media_type VARCHAR(100) NOT NULL,
 file_data MEDIUMBLOB NOT NULL, byte_size INTEGER NOT NULL,
 actor_id VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_launch_attachment ON launch_attachment(kind,entity_id);
