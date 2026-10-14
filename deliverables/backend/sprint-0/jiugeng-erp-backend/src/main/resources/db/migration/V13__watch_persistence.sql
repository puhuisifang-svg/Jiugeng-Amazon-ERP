CREATE TABLE watch_store (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    store_key VARCHAR(100) NOT NULL,
    store_key_normalized VARCHAR(100) NOT NULL,
    store_name VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    marketplace_id VARCHAR(20) NOT NULL DEFAULT 'ATVPDKIKX0DER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    baseline_run_id VARCHAR(36) NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watch_store_key UNIQUE (store_key_normalized),
    CONSTRAINT ck_watch_store_status CHECK (status IN ('ACTIVE','DISABLED'))
);

CREATE TABLE watch_run (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    store_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    adapter_name VARCHAR(100) NOT NULL,
    adapter_version VARCHAR(32) NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    item_count INTEGER NOT NULL,
    unique_asin_count INTEGER NOT NULL,
    page_count INTEGER NOT NULL,
    pages_complete BOOLEAN NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    integrity_status VARCHAR(16) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watch_run_request UNIQUE (request_id),
    CONSTRAINT fk_watch_run_store FOREIGN KEY (store_id) REFERENCES watch_store (id),
    CONSTRAINT ck_watch_run_integrity CHECK (integrity_status IN ('COMPLETE','INCOMPLETE','FAILED')),
    CONSTRAINT ck_watch_run_decision CHECK (decision IN ('PROMOTED','REJECTED_KEEP_PREVIOUS'))
);

ALTER TABLE watch_store ADD CONSTRAINT fk_watch_store_baseline
    FOREIGN KEY (baseline_run_id) REFERENCES watch_run (id);

CREATE TABLE watch_snapshot_asin (
    run_id VARCHAR(36) NOT NULL,
    asin VARCHAR(16) NOT NULL,
    PRIMARY KEY (run_id, asin),
    CONSTRAINT fk_watch_snapshot_run FOREIGN KEY (run_id) REFERENCES watch_run (id) ON DELETE CASCADE
);

CREATE TABLE watch_baseline_asin (
    store_id VARCHAR(36) NOT NULL,
    asin VARCHAR(16) NOT NULL,
    source_run_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (store_id, asin),
    CONSTRAINT fk_watch_baseline_store FOREIGN KEY (store_id) REFERENCES watch_store (id),
    CONSTRAINT fk_watch_baseline_run FOREIGN KEY (source_run_id) REFERENCES watch_run (id)
);

CREATE TABLE watch_new_product_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    event_key VARCHAR(64) NOT NULL,
    store_id VARCHAR(36) NOT NULL,
    detected_run_id VARCHAR(36) NOT NULL,
    asin VARCHAR(16) NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    candidate_id VARCHAR(36) NULL,
    first_detected_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    confirmed_by VARCHAR(36) NULL,
    CONSTRAINT uk_watch_event_key UNIQUE (event_key),
    CONSTRAINT fk_watch_event_store FOREIGN KEY (store_id) REFERENCES watch_store (id),
    CONSTRAINT fk_watch_event_run FOREIGN KEY (detected_run_id) REFERENCES watch_run (id),
    CONSTRAINT fk_watch_event_candidate FOREIGN KEY (candidate_id) REFERENCES selection_candidate (id),
    CONSTRAINT ck_watch_event_status CHECK (status IN ('PENDING_REVIEW','CONFIRMED'))
);

CREATE INDEX idx_watch_run_store_time ON watch_run (store_id, completed_at);
CREATE INDEX idx_watch_event_status_time ON watch_new_product_event (status, first_detected_at);
