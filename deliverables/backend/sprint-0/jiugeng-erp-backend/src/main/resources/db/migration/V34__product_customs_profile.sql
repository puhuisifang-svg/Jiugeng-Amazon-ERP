ALTER TABLE product_variant ADD COLUMN customs_data TEXT;
ALTER TABLE product_variant ADD COLUMN customs_version BIGINT NOT NULL DEFAULT 0;
