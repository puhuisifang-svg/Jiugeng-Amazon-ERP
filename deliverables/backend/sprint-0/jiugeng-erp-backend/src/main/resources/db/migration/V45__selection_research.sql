ALTER TABLE selection_candidate ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE selection_candidate ADD COLUMN research_json TEXT NULL;
ALTER TABLE selection_candidate ADD COLUMN weighted_score DECIMAL(4,1) NULL;
ALTER TABLE product_development ADD COLUMN selection_research_json TEXT NULL;
CREATE TABLE selection_estimate (
 id VARCHAR(36) PRIMARY KEY, candidate_id VARCHAR(36) NOT NULL, request_id VARCHAR(100) NOT NULL,
 payload_json TEXT NOT NULL, result_json TEXT NOT NULL, revision INTEGER NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_selection_estimate_request UNIQUE(candidate_id,request_id),
 CONSTRAINT uk_selection_estimate_revision UNIQUE(candidate_id,revision),
 CONSTRAINT fk_selection_estimate_candidate FOREIGN KEY(candidate_id) REFERENCES selection_candidate(id) ON DELETE CASCADE
);
