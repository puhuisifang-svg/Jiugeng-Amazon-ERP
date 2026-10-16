CREATE TABLE monitor_guard (id INT PRIMARY KEY);
INSERT INTO monitor_guard(id) VALUES(1);

CREATE TABLE monitor_target (
    id VARCHAR(36) PRIMARY KEY,
    marketplace VARCHAR(8) NOT NULL,
    asin VARCHAR(10) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    owner_id VARCHAR(36),
    owner_name VARCHAR(100),
    tags_json TEXT NOT NULL,
    remark VARCHAR(1000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    baseline_json TEXT,
    baseline_source VARCHAR(16),
    baseline_at TIMESTAMP(6),
    last_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_reason VARCHAR(500),
    last_attempt_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_monitor_target UNIQUE(marketplace,asin)
);
CREATE TABLE monitor_snapshot (
    id VARCHAR(36) PRIMARY KEY,
    target_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    source VARCHAR(16) NOT NULL,
    quality VARCHAR(16) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    fields_json TEXT NOT NULL,
    changes_json TEXT NOT NULL,
    CONSTRAINT fk_monitor_snapshot FOREIGN KEY(target_id) REFERENCES monitor_target(id),
    CONSTRAINT uk_monitor_snapshot_request UNIQUE(target_id,request_id)
);
CREATE INDEX idx_monitor_history ON monitor_snapshot(target_id, recorded_at);
CREATE TABLE monitor_request (
    request_id VARCHAR(100) PRIMARY KEY,
    payload_hash CHAR(64) NOT NULL,
    result_json TEXT NOT NULL
);
CREATE TABLE monitor_preference (
    user_id VARCHAR(36) PRIMARY KEY,
    followed_json TEXT NOT NULL,
    columns_json TEXT NOT NULL
);
