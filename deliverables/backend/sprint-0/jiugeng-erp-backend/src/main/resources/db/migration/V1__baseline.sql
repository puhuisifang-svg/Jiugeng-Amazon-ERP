CREATE TABLE schema_baseline (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    module_name VARCHAR(64) NOT NULL,
    baseline_version VARCHAR(32) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_schema_baseline_module UNIQUE (module_name)
);

INSERT INTO schema_baseline (id, module_name, baseline_version)
VALUES ('00000000-0000-0000-0000-000000000001', 'modular-monolith', 'sprint-0');
