-- Copy each existing association once; future customer edits are independent.
-- Keep legacy product data and issued document snapshots for compatibility/history.
ALTER TABLE customer_product ADD COLUMN customs_data TEXT;
UPDATE customer_product SET customs_data=(SELECT v.customs_data FROM product_variant v WHERE v.id=customer_product.variant_id)
WHERE customs_data IS NULL;
