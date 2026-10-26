CREATE TABLE collection_guard(id INTEGER PRIMARY KEY);
INSERT INTO collection_guard VALUES(1);
CREATE TABLE collection_schedule (
 id VARCHAR(36) PRIMARY KEY, kind VARCHAR(20) NOT NULL, target_id VARCHAR(36) NOT NULL, provider VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0, next_at TIMESTAMP NOT NULL,
 UNIQUE(kind,target_id)
);
CREATE TABLE collection_job (
 id VARCHAR(36) PRIMARY KEY, schedule_id VARCHAR(36) NOT NULL, schedule_version BIGINT NOT NULL,
 target_version BIGINT NOT NULL, status VARCHAR(24) NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
 available_at TIMESTAMP NOT NULL, lease_until TIMESTAMP NULL, lease_token VARCHAR(36) NULL,
 reason VARCHAR(200) NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_collection_job_schedule FOREIGN KEY(schedule_id) REFERENCES collection_schedule(id)
);
CREATE INDEX idx_collection_job_due ON collection_job(status,available_at);
CREATE TABLE collection_subscription(user_id VARCHAR(36) PRIMARY KEY, enabled BOOLEAN NOT NULL);
CREATE TABLE collection_notice (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, job_id VARCHAR(36) NOT NULL,
 kind VARCHAR(20) NOT NULL, target_id VARCHAR(36) NOT NULL, message VARCHAR(200) NOT NULL,
 is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,job_id)
);
CREATE TABLE watch_auto_baseline (
 store_id VARCHAR(36) NOT NULL, provider VARCHAR(100) NOT NULL, observed_at TIMESTAMP NOT NULL,
 PRIMARY KEY(store_id,provider)
);
CREATE TABLE watch_presence (
 store_id VARCHAR(36) NOT NULL, provider VARCHAR(100) NOT NULL, asin VARCHAR(16) NOT NULL,
 title VARCHAR(300) NOT NULL, first_at TIMESTAMP NOT NULL, last_at TIMESTAMP NOT NULL,
 missing_day DATE NULL, missing_count INTEGER NOT NULL DEFAULT 0, missing_at TIMESTAMP NULL,
 last_run_id VARCHAR(36) NOT NULL, PRIMARY KEY(store_id,provider,asin)
);
ALTER TABLE monitor_snapshot MODIFY COLUMN source VARCHAR(120) NOT NULL;
ALTER TABLE monitor_target MODIFY COLUMN baseline_source VARCHAR(120) NULL;
