CREATE TABLE customer_order (
 id VARCHAR(36) PRIMARY KEY,
 order_number VARCHAR(48) NOT NULL UNIQUE,
 customer_id VARCHAR(36) NOT NULL,
 customer_name VARCHAR(128) NOT NULL,
 source VARCHAR(16) NOT NULL,
 external_number VARCHAR(100),
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 invoice_required BOOLEAN NOT NULL,
 order_date DATE NOT NULL,
 delivery_date DATE NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 invoice_title_snapshot MEDIUMTEXT NOT NULL,
 net_amount DECIMAL(22,2) NOT NULL,
 tax_amount DECIMAL(22,2) NOT NULL,
 total_amount DECIMAL(22,2) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_order_customer FOREIGN KEY(customer_id) REFERENCES customer(id),
 CONSTRAINT uq_order_external UNIQUE(customer_id,source,external_number)
);
CREATE INDEX idx_customer_order_date ON customer_order(order_date,delivery_date,status);
CREATE TABLE customer_order_command (
 actor_id VARCHAR(36) NOT NULL,
 request_key VARCHAR(128) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 order_id VARCHAR(36) NOT NULL,
 PRIMARY KEY(actor_id,request_key),
 CONSTRAINT fk_order_command FOREIGN KEY(order_id) REFERENCES customer_order(id)
);
CREATE TABLE customer_order_line (
 id VARCHAR(36) PRIMARY KEY,
 order_id VARCHAR(36) NOT NULL,
 position_index INT NOT NULL,
 customer_product_id VARCHAR(36) NOT NULL,
 quantity DECIMAL(12,3) NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 CONSTRAINT fk_order_line_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_order_line_cp FOREIGN KEY(customer_product_id) REFERENCES customer_product(id),
 CONSTRAINT ck_order_line_qty CHECK(quantity>0)
);
CREATE TABLE customer_order_allocation (
 id VARCHAR(36) PRIMARY KEY,
 order_id VARCHAR(36) NOT NULL,
 line_id VARCHAR(36) NOT NULL,
 quantity DECIMAL(12,3) NOT NULL,
 state VARCHAR(16) NOT NULL,
 pending_id VARCHAR(36),
 today_id VARCHAR(36),
 request_key VARCHAR(128) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 logistics VARCHAR(1000) NOT NULL DEFAULT '',
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_allocation_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_allocation_line FOREIGN KEY(line_id) REFERENCES customer_order_line(id),
 CONSTRAINT uq_allocation_request UNIQUE(order_id,request_key,line_id),
 CONSTRAINT ck_allocation_qty CHECK(quantity>0)
);
CREATE TABLE customer_order_invoice (
 id VARCHAR(36) PRIMARY KEY,
 order_id VARCHAR(36) NOT NULL UNIQUE,
 snapshot MEDIUMTEXT NOT NULL,
 invoiced BOOLEAN NOT NULL DEFAULT FALSE,
 invoice_number VARCHAR(100) NOT NULL DEFAULT '',
 invoice_date DATE,
 version BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_order_invoice_order FOREIGN KEY(order_id) REFERENCES customer_order(id)
);
CREATE TABLE customer_order_document (
 id VARCHAR(36) PRIMARY KEY,
 order_id VARCHAR(36) NOT NULL,
 scope VARCHAR(16) NOT NULL,
 file_name VARCHAR(200) NOT NULL,
 byte_size BIGINT NOT NULL,
 content LONGBLOB NOT NULL,
 uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_order_document FOREIGN KEY(order_id) REFERENCES customer_order(id)
);
