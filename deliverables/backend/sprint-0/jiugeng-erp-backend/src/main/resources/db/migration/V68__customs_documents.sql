CREATE TABLE customs_document (
 id VARCHAR(36) PRIMARY KEY,
 document_no VARCHAR(128) NOT NULL UNIQUE,
 status VARCHAR(16) NOT NULL,
 payload LONGTEXT NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_customs_document_updated ON customs_document(updated_at,id);
