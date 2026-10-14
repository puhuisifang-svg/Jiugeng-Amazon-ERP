CREATE TABLE warehouse (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    warehouse_code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_warehouse_code UNIQUE (warehouse_code)
);

INSERT INTO warehouse (id, warehouse_code, name, enabled)
VALUES ('00000000-0000-0000-0000-000000000001', 'MAIN', '固定辅料仓', TRUE);

CREATE TABLE inventory_balance (
    warehouse_id VARCHAR(36) NOT NULL,
    auxiliary_material_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    safety_stock DECIMAL(18, 4) NOT NULL DEFAULT 0,
    unit VARCHAR(32) NOT NULL,
    opening_transaction_id VARCHAR(36) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (warehouse_id, auxiliary_material_id),
    CONSTRAINT fk_inventory_balance_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_inventory_balance_material FOREIGN KEY (auxiliary_material_id) REFERENCES auxiliary_material (id),
    CONSTRAINT ck_inventory_balance_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_balance_safety_stock CHECK (safety_stock >= 0)
);

CREATE TABLE inventory_transaction (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    auxiliary_material_id VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL,
    signed_quantity DECIMAL(18, 4) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reference_transaction_id VARCHAR(36) NULL,
    balance_after DECIMAL(18, 4) NOT NULL,
    actor_user_id VARCHAR(36) NOT NULL,
    actor_login_name VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_transaction_request UNIQUE (request_id),
    CONSTRAINT uk_inventory_transaction_reversal UNIQUE (reference_transaction_id),
    CONSTRAINT fk_inventory_transaction_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_inventory_transaction_material FOREIGN KEY (auxiliary_material_id) REFERENCES auxiliary_material (id),
    CONSTRAINT fk_inventory_transaction_reference FOREIGN KEY (reference_transaction_id) REFERENCES inventory_transaction (id),
    CONSTRAINT ck_inventory_transaction_type CHECK (transaction_type IN
        ('OPENING', 'INBOUND', 'OUTBOUND', 'ADJUST_IN', 'ADJUST_OUT', 'REVERSAL')),
    CONSTRAINT ck_inventory_transaction_quantity CHECK
        ((transaction_type = 'OPENING' AND quantity >= 0) OR (transaction_type <> 'OPENING' AND quantity > 0)),
    CONSTRAINT ck_inventory_transaction_balance CHECK (balance_after >= 0)
);

CREATE INDEX idx_inventory_transaction_material_time
    ON inventory_transaction (auxiliary_material_id, occurred_at);
