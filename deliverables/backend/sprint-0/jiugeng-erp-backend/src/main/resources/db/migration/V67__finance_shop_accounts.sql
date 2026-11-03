CREATE TABLE finance_account (
 id VARCHAR(36) PRIMARY KEY,
 shop_id VARCHAR(36) NOT NULL,
 name VARCHAR(100) NOT NULL,
 name_key VARCHAR(100) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(shop_id) REFERENCES finance_shop(id),
 CONSTRAINT uk_finance_account_shop_name UNIQUE(shop_id,name_key)
);
INSERT INTO finance_account(id,shop_id,name,name_key)
SELECT MIN(id),shop_id,MIN(TRIM(account_name)),LOWER(TRIM(account_name))
FROM finance_bill WHERE TRIM(account_name)<>'' GROUP BY shop_id,LOWER(TRIM(account_name));
