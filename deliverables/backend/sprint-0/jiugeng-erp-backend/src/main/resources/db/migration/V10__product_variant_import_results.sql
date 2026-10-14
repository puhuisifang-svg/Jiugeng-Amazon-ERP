ALTER TABLE product_variant ADD COLUMN package_weight_unit VARCHAR(8) NULL;
UPDATE product_variant SET package_weight = NULL WHERE package_weight = 0;
UPDATE product_variant SET package_weight_unit = 'KG' WHERE package_weight IS NOT NULL;
ALTER TABLE product_variant ADD CONSTRAINT ck_product_variant_weight_unit
    CHECK ((package_weight IS NULL AND package_weight_unit IS NULL)
        OR (package_weight IS NOT NULL AND package_weight > 0 AND package_weight_unit IN ('G','KG','OZ','LB')));

ALTER TABLE import_batch ADD COLUMN result_created_rows INT NOT NULL DEFAULT 0;
ALTER TABLE import_batch ADD COLUMN result_updated_rows INT NOT NULL DEFAULT 0;
ALTER TABLE import_batch ADD COLUMN result_unchanged_rows INT NOT NULL DEFAULT 0;
ALTER TABLE import_batch ADD COLUMN committed_at TIMESTAMP NULL;
