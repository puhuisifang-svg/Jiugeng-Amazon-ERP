ALTER TABLE collection_schedule ADD COLUMN interval_days INTEGER NOT NULL DEFAULT 1;
ALTER TABLE collection_schedule ADD COLUMN local_time VARCHAR(5) NOT NULL DEFAULT '03:00';
ALTER TABLE collection_schedule ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'America/Los_Angeles';
ALTER TABLE collection_schedule ADD COLUMN timing_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE collection_schedule ADD COLUMN manual_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE collection_schedule ADD CONSTRAINT ck_collection_interval_days CHECK (interval_days BETWEEN 1 AND 30);
