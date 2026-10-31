CREATE TABLE launch_workspace (
 kind VARCHAR(16) NOT NULL, entity_id VARCHAR(36) NOT NULL,
 state VARCHAR(24) NOT NULL, stage INTEGER NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0, document_json MEDIUMTEXT NOT NULL,
 baseline_json MEDIUMTEXT NULL, proposal_json MEDIUMTEXT NULL,
 resume_state VARCHAR(24) NULL, risk_review_json TEXT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(kind,entity_id)
);
CREATE TABLE launch_revision (
 id VARCHAR(36) PRIMARY KEY, kind VARCHAR(16) NOT NULL, entity_id VARCHAR(36) NOT NULL,
 version BIGINT NOT NULL, action VARCHAR(40) NOT NULL, actor_id VARCHAR(36) NOT NULL,
 actor_name VARCHAR(200) NOT NULL, reason VARCHAR(2000), snapshot_json MEDIUMTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_launch_revision UNIQUE(kind,entity_id,version)
);
CREATE INDEX idx_launch_history ON launch_revision(kind,entity_id,created_at);
