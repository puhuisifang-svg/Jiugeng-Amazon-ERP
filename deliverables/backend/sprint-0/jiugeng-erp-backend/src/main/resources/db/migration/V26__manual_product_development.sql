ALTER TABLE product_development MODIFY COLUMN candidate_id VARCHAR(36) NULL;
ALTER TABLE product_development MODIFY COLUMN source_asin VARCHAR(16) NULL;
ALTER TABLE product_development MODIFY COLUMN source_title VARCHAR(300) NULL;
ALTER TABLE product_development MODIFY COLUMN source_score INT NULL;
ALTER TABLE product_development MODIFY COLUMN source_url VARCHAR(1000) NULL;
ALTER TABLE product_development ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'CANDIDATE';
ALTER TABLE product_development ADD COLUMN manual_request_id VARCHAR(36) NULL;
ALTER TABLE product_development ADD COLUMN manual_actor_id VARCHAR(36) NULL;
ALTER TABLE product_development ADD COLUMN manual_payload_hash VARCHAR(64) NULL;
ALTER TABLE product_development ADD CONSTRAINT uk_development_manual_request UNIQUE(manual_request_id);
ALTER TABLE product_development ADD CONSTRAINT ck_development_source CHECK (
    (source_type='CANDIDATE' AND candidate_id IS NOT NULL AND source_asin IS NOT NULL
        AND source_title IS NOT NULL AND source_score IS NOT NULL AND source_url IS NOT NULL
        AND manual_request_id IS NULL AND manual_actor_id IS NULL AND manual_payload_hash IS NULL)
    OR (source_type='MANUAL' AND candidate_id IS NULL AND source_asin IS NULL AND source_title IS NULL
        AND source_score IS NULL AND source_url IS NULL AND source_category IS NULL
        AND source_price IS NULL AND source_reason IS NULL
        AND manual_request_id IS NOT NULL AND manual_actor_id IS NOT NULL AND manual_payload_hash IS NOT NULL)
);

-- Serializes only manual creation, including idempotency checks and readable numbering.
CREATE TABLE product_development_create_lock (
    id INT PRIMARY KEY,
    next_number BIGINT NOT NULL,
    CONSTRAINT ck_development_lock CHECK(id=1 AND next_number>0)
);
INSERT INTO product_development_create_lock(id,next_number) VALUES(1,1);
