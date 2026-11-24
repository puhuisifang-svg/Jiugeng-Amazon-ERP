-- User-requested one-time removal of existing supplier change history (2026-11-24).
-- Back up before applying; preserve material relationship and security audit events.
DELETE FROM audit_event
WHERE category = 'BUSINESS'
  AND resource_type = 'SUPPLIER'
  AND action IN ('SUPPLIER_CREATE', 'SUPPLIER_UPDATE', 'SUPPLIER_DELETE', 'SUPPLIER_ARCHIVED_LINK_REMOVE');
