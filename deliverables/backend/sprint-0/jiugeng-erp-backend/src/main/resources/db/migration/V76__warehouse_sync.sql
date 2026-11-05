ALTER TABLE customs_warehouse ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
CREATE TABLE customs_warehouse_sync (
 id INTEGER PRIMARY KEY,
 state VARCHAR(16) NOT NULL,
 started_at TIMESTAMP NULL,
 finished_at TIMESTAMP NULL,
 next_run_at TIMESTAMP NOT NULL,
 lease_until TIMESTAMP NULL,
 message VARCHAR(2000) NOT NULL,
 source_date VARCHAR(30) NOT NULL DEFAULT '',
 added INTEGER NOT NULL DEFAULT 0,
 updated INTEGER NOT NULL DEFAULT 0,
 preserved INTEGER NOT NULL DEFAULT 0,
 rejected INTEGER NOT NULL DEFAULT 0
);
INSERT INTO customs_warehouse_sync(id,state,next_run_at,message) VALUES(1,'IDLE',CURRENT_TIMESTAMP,'尚未更新');
