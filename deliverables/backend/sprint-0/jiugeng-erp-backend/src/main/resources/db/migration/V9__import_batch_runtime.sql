CREATE TABLE import_batch (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    import_type VARCHAR(64) NOT NULL,
    template_version VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    upload_idempotency_key VARCHAR(128) NOT NULL,
    upload_payload_hash VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    actor_login_name VARCHAR(100) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    processed_rows INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    commit_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    preview_version VARCHAR(36) NULL,
    preview_expires_at TIMESTAMP NULL,
    revalidation_of_batch_id VARCHAR(36) NULL,
    result_storage_key VARCHAR(100) NULL,
    failure_code VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_import_upload_idempotency UNIQUE (actor_user_id, upload_idempotency_key),
    CONSTRAINT uk_import_revalidation_source UNIQUE (revalidation_of_batch_id),
    CONSTRAINT fk_import_revalidation_source FOREIGN KEY (revalidation_of_batch_id) REFERENCES import_batch (id),
    CONSTRAINT ck_import_batch_type CHECK (import_type IN ('PRODUCTS','VARIANTS','AUXILIARY_MATERIALS','PRODUCT_AUXILIARY_MATERIALS','AUXILIARY_MATERIAL_OPENING_INVENTORY')),
    CONSTRAINT ck_import_batch_status CHECK (status IN ('QUEUED','VALIDATING','VALIDATED','COMMITTING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT ck_import_batch_phase CHECK (phase IN ('WAITING','PARSING','VALIDATING_ROWS','BUILDING_PREVIEW','WAITING_CONFIRMATION','COMMITTING_ROWS','FINALIZING','DONE'))
);

CREATE TABLE import_issue (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    blocking BOOLEAN NOT NULL,
    issue_scope VARCHAR(16) NOT NULL,
    sheet_name VARCHAR(100) NULL,
    source_row_number INT NULL,
    field_name VARCHAR(100) NULL,
    issue_code VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    raw_value VARCHAR(500) NULL,
    suggestion VARCHAR(500) NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_import_issue_batch FOREIGN KEY (batch_id) REFERENCES import_batch (id) ON DELETE CASCADE
);
CREATE INDEX idx_import_issue_batch_order ON import_issue (batch_id, sort_order);

CREATE TABLE import_preview_row (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    sheet_name VARCHAR(100) NOT NULL,
    source_row_number INT NOT NULL,
    business_key VARCHAR(500) NOT NULL,
    row_action VARCHAR(16) NULL,
    row_data_json TEXT NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_import_preview_batch FOREIGN KEY (batch_id) REFERENCES import_batch (id) ON DELETE CASCADE
);
CREATE INDEX idx_import_preview_batch_order ON import_preview_row (batch_id, sort_order);

CREATE TABLE import_operation_idempotency (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    actor_user_id VARCHAR(36) NOT NULL,
    operation_name VARCHAR(32) NOT NULL,
    object_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    response_batch_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_import_operation_idempotency UNIQUE (actor_user_id, operation_name, object_id, idempotency_key),
    CONSTRAINT fk_import_operation_response FOREIGN KEY (response_batch_id) REFERENCES import_batch (id)
);
