CREATE TABLE finance_shop (
 id VARCHAR(36) PRIMARY KEY,
 name VARCHAR(128) NOT NULL,
 name_key VARCHAR(128) NOT NULL UNIQUE,
 remark VARCHAR(500) NOT NULL DEFAULT '',
 active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE finance_bill (
 id VARCHAR(36) PRIMARY KEY,
 shop_id VARCHAR(36) NOT NULL,
 bill_date DATE NOT NULL,
 direction VARCHAR(10) NOT NULL,
 category VARCHAR(64) NOT NULL,
 detail VARCHAR(500) NOT NULL,
 account_name VARCHAR(100) NOT NULL,
 amount DECIMAL(16,2) NOT NULL,
 remark VARCHAR(1000) NOT NULL DEFAULT '',
 voided BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(shop_id) REFERENCES finance_shop(id),
 CONSTRAINT ck_finance_direction CHECK(direction IN ('INCOME','EXPENSE')),
 CONSTRAINT ck_finance_amount CHECK(amount>0)
);
CREATE INDEX idx_finance_shop_date ON finance_bill(shop_id,voided,bill_date,id);
CREATE TABLE finance_attachment (
 id VARCHAR(36) PRIMARY KEY,
 bill_id VARCHAR(36) NOT NULL,
 file_name VARCHAR(200) NOT NULL,
 media_type VARCHAR(64) NOT NULL,
 file_data MEDIUMBLOB NOT NULL,
 byte_size BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(bill_id) REFERENCES finance_bill(id)
);
CREATE INDEX idx_finance_attachment_bill ON finance_attachment(bill_id);
