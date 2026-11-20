CREATE TABLE product_inventory_balance (
    variant_id VARCHAR(36) NOT NULL PRIMARY KEY,
    quantity DECIMAL(15,0) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_product_stock_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id),
    CONSTRAINT ck_product_stock_quantity CHECK (quantity >= 0)
);
CREATE TABLE product_inventory_transaction (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL,
    variant_id VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    quantity DECIMAL(15,0) NOT NULL,
    signed_quantity DECIMAL(15,0) NOT NULL,
    balance_after DECIMAL(15,0) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    actor_login_name VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_product_stock_request UNIQUE (variant_id,request_id),
    CONSTRAINT fk_product_stock_transaction_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id),
    CONSTRAINT ck_product_stock_transaction_quantity CHECK (quantity > 0),
    CONSTRAINT ck_product_stock_transaction_balance CHECK (balance_after >= 0)
);
CREATE INDEX idx_product_stock_transaction_time ON product_inventory_transaction(variant_id,occurred_at);
