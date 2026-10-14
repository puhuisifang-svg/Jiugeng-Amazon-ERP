CREATE TABLE audit_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(36) NULL,
    actor_login_name VARCHAR(128) NOT NULL,
    actor_display_name VARCHAR(128) NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    outcome VARCHAR(16) NOT NULL,
    before_value TEXT NULL,
    after_value TEXT NULL,
    detail VARCHAR(255) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_event_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

INSERT INTO audit_event (
    id, category, action, actor_user_id, actor_login_name, resource_type, resource_id,
    outcome, detail, occurred_at
)
SELECT
    id, 'SECURITY', event_type, user_id, login_name, 'ACCOUNT',
    COALESCE(user_id, login_name), CASE WHEN success THEN 'SUCCESS' ELSE 'FAILURE' END,
    detail, occurred_at
FROM iam_security_event;

DROP TABLE iam_security_event;

CREATE INDEX idx_audit_event_time ON audit_event (occurred_at);
CREATE INDEX idx_audit_event_actor_time ON audit_event (actor_user_id, occurred_at);
CREATE INDEX idx_audit_event_resource_time ON audit_event (resource_type, resource_id, occurred_at);
CREATE INDEX idx_audit_event_action_time ON audit_event (action, occurred_at);
