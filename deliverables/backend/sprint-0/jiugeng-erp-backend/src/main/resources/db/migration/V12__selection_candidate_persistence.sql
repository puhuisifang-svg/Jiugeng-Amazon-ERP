CREATE TABLE selection_candidate (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    asin VARCHAR(16) NOT NULL,
    asin_normalized VARCHAR(16) NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL DEFAULT 50,
    rejection_reason VARCHAR(500) NULL,
    source_url VARCHAR(1000) NOT NULL,
    category VARCHAR(100) NULL,
    price DECIMAL(18, 4) NULL,
    rating DECIMAL(3, 2) NULL,
    review_count INTEGER NULL,
    owner VARCHAR(100) NULL,
    selection_reason VARCHAR(1000) NULL,
    source VARCHAR(16) NOT NULL,
    source_store VARCHAR(200) NULL,
    discovered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_run_id VARCHAR(100) NULL,
    source_event_id VARCHAR(100) NULL,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_selection_candidate_asin UNIQUE (asin_normalized),
    CONSTRAINT ck_selection_candidate_status CHECK (status IN ('NEW', 'REVIEWING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_selection_candidate_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_selection_candidate_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT ck_selection_candidate_rating CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5)),
    CONSTRAINT ck_selection_candidate_reviews CHECK (review_count IS NULL OR review_count >= 0)
);

CREATE TABLE selection_candidate_tag (
    candidate_id VARCHAR(36) NOT NULL,
    tag VARCHAR(64) NOT NULL,
    tag_normalized VARCHAR(64) NOT NULL,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (candidate_id, tag_normalized),
    CONSTRAINT fk_selection_tag_candidate FOREIGN KEY (candidate_id)
        REFERENCES selection_candidate (id) ON DELETE CASCADE
);

CREATE TABLE selection_candidate_note (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    author_name VARCHAR(200) NOT NULL,
    author_role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_selection_note_candidate FOREIGN KEY (candidate_id)
        REFERENCES selection_candidate (id) ON DELETE CASCADE
);

CREATE TABLE selection_candidate_evidence (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    description VARCHAR(1000) NULL,
    added_by_id VARCHAR(36) NOT NULL,
    added_by_name VARCHAR(200) NOT NULL,
    added_by_role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_selection_evidence_candidate FOREIGN KEY (candidate_id)
        REFERENCES selection_candidate (id) ON DELETE CASCADE
);

CREATE INDEX idx_selection_candidate_status_owner ON selection_candidate (status, owner);
CREATE INDEX idx_selection_note_candidate_time ON selection_candidate_note (candidate_id, created_at);
CREATE INDEX idx_selection_evidence_candidate_time ON selection_candidate_evidence (candidate_id, created_at);
