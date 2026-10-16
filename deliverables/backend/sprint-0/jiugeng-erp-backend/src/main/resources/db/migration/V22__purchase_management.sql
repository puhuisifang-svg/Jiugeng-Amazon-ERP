CREATE TABLE purchase_order (
    id VARCHAR(36) PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    remark VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_order_no UNIQUE (order_no),
    CONSTRAINT fk_purchase_order_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT ck_purchase_order_type CHECK (order_type IN ('PRODUCT','MATERIAL')),
    CONSTRAINT ck_purchase_order_status CHECK (status IN ('DRAFT','SUBMITTED','PARTIALLY_RECEIVED','COMPLETED','CANCELLED'))
);

CREATE TABLE purchase_order_line (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    line_no INT NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    ordered_quantity DECIMAL(18,4) NOT NULL,
    received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(18,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_line_order FOREIGN KEY (order_id) REFERENCES purchase_order(id) ON DELETE CASCADE,
    CONSTRAINT uk_purchase_line_no UNIQUE (order_id, line_no),
    CONSTRAINT uk_purchase_line_item UNIQUE (order_id, item_id),
    CONSTRAINT ck_purchase_line_quantity CHECK (ordered_quantity > 0 AND received_quantity >= 0 AND received_quantity <= ordered_quantity),
    CONSTRAINT ck_purchase_line_price CHECK (unit_price >= 0)
);

CREATE TABLE purchase_receipt (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    remark VARCHAR(500),
    actor_user_id VARCHAR(36) NOT NULL,
    actor_login_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_receipt_order FOREIGN KEY (order_id) REFERENCES purchase_order(id),
    CONSTRAINT uk_purchase_receipt_request UNIQUE (request_id)
);

CREATE TABLE purchase_receipt_line (
    id VARCHAR(36) PRIMARY KEY,
    receipt_id VARCHAR(36) NOT NULL,
    order_line_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    inventory_transaction_id VARCHAR(36),
    CONSTRAINT fk_purchase_receipt_line_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_receipt(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_receipt_line_order_line FOREIGN KEY (order_line_id) REFERENCES purchase_order_line(id),
    CONSTRAINT uk_purchase_receipt_line UNIQUE (receipt_id, order_line_id),
    CONSTRAINT ck_purchase_receipt_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_purchase_order_status ON purchase_order(status, updated_at);
CREATE INDEX idx_purchase_order_supplier ON purchase_order(supplier_id, created_at);
CREATE INDEX idx_purchase_receipt_order ON purchase_receipt(order_id, created_at);

