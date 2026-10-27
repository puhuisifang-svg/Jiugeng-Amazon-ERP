ALTER TABLE watch_new_product_event ADD COLUMN novelty_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE watch_new_product_event ADD COLUMN available_date DATE NULL;
ALTER TABLE watch_new_product_event ADD COLUMN novelty_reason VARCHAR(1000) NOT NULL DEFAULT '等待补采亚马逊首次可用日期';
ALTER TABLE watch_new_product_event ADD COLUMN novelty_checked_at TIMESTAMP(6) NULL;
ALTER TABLE watch_new_product_event ADD COLUMN novelty_override VARCHAR(30) NULL;
ALTER TABLE watch_new_product_event ADD COLUMN novelty_version BIGINT NOT NULL DEFAULT 0;
