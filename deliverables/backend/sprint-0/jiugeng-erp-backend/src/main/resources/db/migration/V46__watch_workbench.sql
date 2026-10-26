ALTER TABLE watch_store ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE watch_store ADD COLUMN owner_name VARCHAR(100) NULL;
ALTER TABLE watch_store ADD COLUMN remark VARCHAR(1000) NULL;
ALTER TABLE watch_new_product_event ADD COLUMN ignored BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE watch_new_product_event ADD COLUMN ignore_reason VARCHAR(1000) NULL;
ALTER TABLE watch_new_product_event ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE selection_source_link (
 candidate_id VARCHAR(36) NOT NULL, event_id VARCHAR(36) NOT NULL, source_run_id VARCHAR(100) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(candidate_id,event_id),
 CONSTRAINT fk_selection_source_candidate FOREIGN KEY(candidate_id) REFERENCES selection_candidate(id) ON DELETE CASCADE
);
