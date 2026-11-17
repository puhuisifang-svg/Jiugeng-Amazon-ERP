ALTER TABLE customer ADD export_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customer ADD export_company_name VARCHAR(300) NOT NULL DEFAULT '';
ALTER TABLE customer ADD export_company_prefix VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE customer ADD export_company_address VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE customer ADD export_company_phone VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE customer ADD export_company_fax VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE customs_document ADD customer_id VARCHAR(36) NULL;
ALTER TABLE customs_document ADD CONSTRAINT fk_customs_customer FOREIGN KEY(customer_id) REFERENCES customer(id);
CREATE INDEX idx_customs_customer ON customs_document(customer_id,deleted_at);
