CREATE TABLE product_development (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    number VARCHAR(24) NOT NULL,
    name VARCHAR(300) NOT NULL,
    developer VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    source_asin VARCHAR(16) NOT NULL,
    source_title VARCHAR(300) NOT NULL,
    source_category VARCHAR(100),
    source_score INT NOT NULL,
    source_price DECIMAL(18,4),
    source_url VARCHAR(1000) NOT NULL,
    source_reason VARCHAR(1000),
    fields_json TEXT NOT NULL,
    void_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_development_candidate UNIQUE(candidate_id),
    CONSTRAINT uk_development_number UNIQUE(number),
    CONSTRAINT fk_development_candidate FOREIGN KEY(candidate_id) REFERENCES selection_candidate(id),
    CONSTRAINT ck_development_status CHECK(status IN ('DRAFT','IN_PROGRESS','COMPLETED','VOID'))
);
CREATE INDEX idx_development_status ON product_development(status, updated_at);

-- Historical approvals are real existing records, not synthetic seed data.
INSERT INTO product_development(id,candidate_id,number,name,developer,source_asin,source_title,
    source_category,source_score,source_price,source_url,source_reason,fields_json)
SELECT id,id,CONCAT('ND-',asin),title,owner,asin,title,category,score,price,source_url,selection_reason,'{}'
FROM selection_candidate WHERE status='APPROVED';
