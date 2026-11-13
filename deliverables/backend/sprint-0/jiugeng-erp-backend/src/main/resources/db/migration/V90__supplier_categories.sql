ALTER TABLE supplier ADD COLUMN categories VARCHAR(32) NOT NULL DEFAULT '';
UPDATE supplier SET categories = CASE
 WHEN (EXISTS (SELECT 1 FROM product_variant_supplier p WHERE p.supplier_id=supplier.id) OR EXISTS (SELECT 1 FROM purchase_order o WHERE o.supplier_id=supplier.id AND o.order_type='PRODUCT'))
 THEN CASE WHEN (EXISTS (SELECT 1 FROM auxiliary_material_supplier m WHERE m.supplier_id=supplier.id) OR EXISTS (SELECT 1 FROM purchase_order o WHERE o.supplier_id=supplier.id AND o.order_type='MATERIAL')) THEN 'MATERIAL,PRODUCT' ELSE 'PRODUCT' END
 WHEN (EXISTS (SELECT 1 FROM auxiliary_material_supplier m WHERE m.supplier_id=supplier.id) OR EXISTS (SELECT 1 FROM purchase_order o WHERE o.supplier_id=supplier.id AND o.order_type='MATERIAL')) THEN 'MATERIAL'
 ELSE '' END;
