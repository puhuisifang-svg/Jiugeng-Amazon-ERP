CREATE TABLE customer_product_label (
 id VARCHAR(36) PRIMARY KEY,
 customer_product_id VARCHAR(36) NOT NULL,
 kind VARCHAR(20) NOT NULL DEFAULT 'LABEL',
 file_name VARCHAR(200) NOT NULL,
 byte_size BIGINT NOT NULL,
 content LONGBLOB NOT NULL,
 uploaded_by VARCHAR(36) NOT NULL,
 uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_customer_product_label_owner FOREIGN KEY (customer_product_id) REFERENCES customer_product(id)
);
CREATE INDEX idx_customer_product_label_owner ON customer_product_label(customer_product_id, uploaded_at);
-- Retired drafts remain inactive; existing enabled roles are unchanged.
UPDATE iam_role SET status='DISABLED', version=version+1 WHERE status='DRAFT';
