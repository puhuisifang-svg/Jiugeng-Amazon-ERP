CREATE TABLE iam_user (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    login_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_iam_user_login_name UNIQUE (login_name),
    CONSTRAINT ck_iam_user_role CHECK (role IN ('ADMIN', 'PRODUCT_MATERIAL', 'SELECTION')),
    CONSTRAINT ck_iam_user_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE TABLE iam_session (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_iam_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_iam_session_user FOREIGN KEY (user_id) REFERENCES iam_user (id)
);

CREATE INDEX idx_iam_session_user ON iam_session (user_id);

CREATE TABLE iam_security_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NULL,
    login_name VARCHAR(128) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    success BOOLEAN NOT NULL,
    detail VARCHAR(255) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_iam_security_event_user_time ON iam_security_event (user_id, occurred_at);
