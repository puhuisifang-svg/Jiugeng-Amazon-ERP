ALTER TABLE supplier ADD COLUMN wechat_id VARCHAR(100) NULL AFTER phone;
ALTER TABLE supplier ADD COLUMN qr_image_url VARCHAR(500) NULL AFTER source_url;
ALTER TABLE supplier DROP CONSTRAINT ck_supplier_contact;
ALTER TABLE supplier ADD CONSTRAINT ck_supplier_contact CHECK (
    phone IS NOT NULL
    OR wechat_id IS NOT NULL
    OR source_url IS NOT NULL
    OR qr_image_url IS NOT NULL
    OR qr_image_asset_id IS NOT NULL
);
