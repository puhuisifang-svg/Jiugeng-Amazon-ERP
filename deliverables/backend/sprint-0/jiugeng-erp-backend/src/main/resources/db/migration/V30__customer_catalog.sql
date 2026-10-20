CREATE TABLE customer (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    account_1688 VARCHAR(100) NOT NULL DEFAULT '',
    contact_person VARCHAR(64) NOT NULL DEFAULT '',
    phone VARCHAR(32) NOT NULL DEFAULT '',
    wechat_id VARCHAR(100) NOT NULL DEFAULT '',
    address VARCHAR(500) NOT NULL DEFAULT '',
    remark VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE INDEX idx_customer_status_name ON customer(status, name);

CREATE TABLE customer_product (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NOT NULL,
    sku_code VARCHAR(128) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    supply_price DECIMAL(14,2) NOT NULL,
    price_unit VARCHAR(16) NOT NULL,
    customer_product_code VARCHAR(128) NOT NULL DEFAULT '',
    requirements VARCHAR(2000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_product_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_customer_product_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_customer_product_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id),
    CONSTRAINT uk_customer_product_sku UNIQUE (customer_id, variant_id),
    CONSTRAINT ck_customer_product_price CHECK (supply_price >= 0),
    CONSTRAINT ck_customer_product_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
