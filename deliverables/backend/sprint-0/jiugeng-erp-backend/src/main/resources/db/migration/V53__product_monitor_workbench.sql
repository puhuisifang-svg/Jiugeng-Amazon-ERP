ALTER TABLE monitor_target ADD COLUMN removed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitor_target ADD COLUMN sales_30_days BIGINT NULL;
ALTER TABLE monitor_target ADD COLUMN latest_changes_json TEXT NULL;
