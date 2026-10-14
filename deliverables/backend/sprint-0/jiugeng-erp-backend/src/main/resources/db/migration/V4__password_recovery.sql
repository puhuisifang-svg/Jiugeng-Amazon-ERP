ALTER TABLE iam_user ADD COLUMN email VARCHAR(254) NULL;
ALTER TABLE iam_user ADD COLUMN phone VARCHAR(16) NULL;

CREATE UNIQUE INDEX uk_iam_user_email ON iam_user (email);
CREATE UNIQUE INDEX uk_iam_user_phone ON iam_user (phone);

CREATE TABLE iam_password_reset_challenge (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    destination VARCHAR(254) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    remaining_attempts INT NOT NULL,
    consumed_at TIMESTAMP NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES iam_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_lookup
    ON iam_password_reset_challenge (channel, destination, requested_at);
CREATE INDEX idx_password_reset_user
    ON iam_password_reset_challenge (user_id, channel, requested_at);
