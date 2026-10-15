CREATE TABLE dashboard_image_asset (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    original_storage_key VARCHAR(100) NOT NULL,
    thumbnail_storage_key VARCHAR(100) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    source_media_type VARCHAR(32) NOT NULL,
    stored_media_type VARCHAR(32) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    pixel_width INTEGER NOT NULL,
    pixel_height INTEGER NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    attached_resource_type VARCHAR(32) NULL,
    attached_resource_id VARCHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attached_at TIMESTAMP NULL,
    CONSTRAINT uk_dashboard_image_original_key UNIQUE (original_storage_key),
    CONSTRAINT uk_dashboard_image_thumbnail_key UNIQUE (thumbnail_storage_key),
    CONSTRAINT ck_dashboard_image_purpose CHECK (purpose IN ('SHIPMENT','PENDING_PRODUCT')),
    CONSTRAINT ck_dashboard_image_status CHECK (status IN ('PENDING','ACTIVE')),
    CONSTRAINT ck_dashboard_image_size CHECK (byte_size > 0 AND byte_size <= 5242880),
    CONSTRAINT ck_dashboard_image_dimensions CHECK (
        pixel_width > 0 AND pixel_width <= 4096 AND pixel_height > 0 AND pixel_height <= 4096
        AND pixel_width * pixel_height <= 16000000)
);

CREATE TABLE dashboard_pending_shipment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    planned_ship_date DATE NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_code_snapshot VARCHAR(64) NOT NULL,
    product_name_snapshot VARCHAR(128) NOT NULL,
    image_asset_id VARCHAR(36) NULL,
    quantity INTEGER NOT NULL,
    quantity_unit VARCHAR(8) NOT NULL DEFAULT 'SET',
    box_count INTEGER NOT NULL,
    remark VARCHAR(500) NULL,
    latest_ship_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    archived_by VARCHAR(36) NULL,
    archived_at TIMESTAMP NULL,
    archive_reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dashboard_pending_shipment_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_dashboard_pending_shipment_image FOREIGN KEY (image_asset_id) REFERENCES dashboard_image_asset (id),
    CONSTRAINT ck_dashboard_pending_shipment_status CHECK (status IN ('PENDING','TRANSFERRED_TO_TODAY','ARCHIVED')),
    CONSTRAINT ck_dashboard_pending_shipment_quantity CHECK (quantity > 0),
    CONSTRAINT ck_dashboard_pending_shipment_boxes CHECK (box_count > 0),
    CONSTRAINT ck_dashboard_pending_shipment_unit CHECK (quantity_unit = 'SET')
);

CREATE TABLE dashboard_today_shipment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    ship_date DATE NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_code_snapshot VARCHAR(64) NOT NULL,
    product_name_snapshot VARCHAR(128) NOT NULL,
    image_asset_id VARCHAR(36) NULL,
    quantity INTEGER NOT NULL,
    quantity_unit VARCHAR(8) NOT NULL DEFAULT 'SET',
    box_count INTEGER NOT NULL,
    remark VARCHAR(500) NULL,
    latest_ship_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    source_pending_shipment_id VARCHAR(36) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    archived_by VARCHAR(36) NULL,
    archived_at TIMESTAMP NULL,
    archive_reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dashboard_today_shipment_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_dashboard_today_shipment_image FOREIGN KEY (image_asset_id) REFERENCES dashboard_image_asset (id),
    CONSTRAINT fk_dashboard_today_source_pending FOREIGN KEY (source_pending_shipment_id)
        REFERENCES dashboard_pending_shipment (id),
    CONSTRAINT uk_dashboard_today_source_pending UNIQUE (source_pending_shipment_id),
    CONSTRAINT ck_dashboard_today_shipment_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_dashboard_today_shipment_quantity CHECK (quantity > 0),
    CONSTRAINT ck_dashboard_today_shipment_boxes CHECK (box_count > 0),
    CONSTRAINT ck_dashboard_today_shipment_unit CHECK (quantity_unit = 'SET')
);

CREATE TABLE dashboard_pending_product (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_name VARCHAR(128) NOT NULL,
    image_asset_id VARCHAR(36) NULL,
    expected_quantity INTEGER NOT NULL,
    quantity_unit VARCHAR(8) NOT NULL DEFAULT 'SET',
    remark VARCHAR(500) NULL,
    expected_completion_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    updated_by VARCHAR(36) NOT NULL,
    completed_by VARCHAR(36) NULL,
    completed_at TIMESTAMP NULL,
    archived_by VARCHAR(36) NULL,
    archived_at TIMESTAMP NULL,
    archive_reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dashboard_pending_product_image FOREIGN KEY (image_asset_id) REFERENCES dashboard_image_asset (id),
    CONSTRAINT ck_dashboard_pending_product_status CHECK (status IN ('PENDING','COMPLETED','ARCHIVED')),
    CONSTRAINT ck_dashboard_pending_product_quantity CHECK (expected_quantity > 0),
    CONSTRAINT ck_dashboard_pending_product_unit CHECK (quantity_unit = 'SET')
);

CREATE TABLE dashboard_command (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    actor_user_id VARCHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dashboard_command UNIQUE (actor_user_id, operation, idempotency_key)
);

CREATE INDEX idx_dashboard_image_pending_time ON dashboard_image_asset (status, created_at);
CREATE INDEX idx_dashboard_pending_shipment_home ON dashboard_pending_shipment
    (status, planned_ship_date, latest_ship_at, product_code_snapshot, id);
CREATE INDEX idx_dashboard_today_shipment_home ON dashboard_today_shipment
    (status, ship_date, latest_ship_at, product_code_snapshot, id);
CREATE INDEX idx_dashboard_pending_product_home ON dashboard_pending_product
    (status, expected_completion_date, created_at, id);
