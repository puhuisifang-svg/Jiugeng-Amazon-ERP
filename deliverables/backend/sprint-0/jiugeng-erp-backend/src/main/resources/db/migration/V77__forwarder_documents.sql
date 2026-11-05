CREATE TABLE forwarder_document (
 id VARCHAR(36) PRIMARY KEY,
 document_no VARCHAR(128) NOT NULL,
 carrier VARCHAR(16) NOT NULL,
 template_version VARCHAR(20) NOT NULL,
 status VARCHAR(16) NOT NULL,
 payload LONGTEXT NOT NULL,
 waybill VARCHAR(2000) NOT NULL,
 warehouse VARCHAR(2000) NOT NULL,
 item_count INT NOT NULL,
 cartons INT NOT NULL,
 amount DECIMAL(24,2) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at TIMESTAMP NULL,
 deletion_key VARCHAR(36) NOT NULL DEFAULT '',
 UNIQUE(document_no,deletion_key)
);

INSERT INTO iam_role_permission(role_id,permission_code) VALUES
('ADMIN','forwarders.read'),('ADMIN','forwarders.write'),('ADMIN','forwarders.export'),('ADMIN','forwarders.delete');
