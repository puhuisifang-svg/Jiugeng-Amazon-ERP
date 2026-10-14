CREATE TABLE auxiliary_material (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    material_code VARCHAR(64) NOT NULL,
    material_code_normalized VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    material_type VARCHAR(100) NOT NULL,
    specification VARCHAR(300) NULL,
    unit VARCHAR(32) NOT NULL,
    image_url VARCHAR(500) NULL,
    remark VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auxiliary_material_code_normalized UNIQUE (material_code_normalized),
    CONSTRAINT ck_auxiliary_material_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_auxiliary_material_status_code
    ON auxiliary_material (status, material_code_normalized);
