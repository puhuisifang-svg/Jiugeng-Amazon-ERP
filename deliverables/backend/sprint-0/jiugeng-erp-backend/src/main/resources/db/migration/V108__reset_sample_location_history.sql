-- User-requested one-time reset of sample change history (2026-11-24).
-- Back up the database before applying. Keep other modules and security audit events.
-- Subsequent sample history records contain only actual location changes.
DELETE FROM audit_event
WHERE category = 'BUSINESS'
  AND resource_type = 'SAMPLE_LOCATION'
  AND action IN ('SAMPLE_CREATE', 'SAMPLE_COPY', 'SAMPLE_UPDATE', 'SAMPLE_REMOVE', 'SAMPLE_RESTORE');
