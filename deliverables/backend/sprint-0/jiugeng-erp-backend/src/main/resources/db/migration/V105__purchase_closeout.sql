ALTER TABLE purchase_order ADD COLUMN snapshot_saved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE purchase_order_line ADD COLUMN snapshot_saved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE purchase_order_line ADD COLUMN snapshot_image_url VARCHAR(2048);
ALTER TABLE purchase_receipt ADD COLUMN shipment_id VARCHAR(36);
CREATE TABLE purchase_write_request (
 actor_id VARCHAR(36) NOT NULL, request_id VARCHAR(100) NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 order_id VARCHAR(36) NOT NULL, PRIMARY KEY(actor_id,request_id)
);
CREATE TABLE purchase_shipment (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, request_id VARCHAR(100) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, actor_id VARCHAR(36) NOT NULL, carrier VARCHAR(100), tracking_number VARCHAR(64),
 image_id VARCHAR(36), shipped_on DATE NOT NULL, package_count INT NOT NULL, remark VARCHAR(500),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_purchase_shipment_order FOREIGN KEY(order_id) REFERENCES purchase_order(id) ON DELETE CASCADE,
 CONSTRAINT uk_purchase_shipment_request UNIQUE(actor_id,request_id)
);
CREATE TABLE purchase_shipment_line (
 shipment_id VARCHAR(36) NOT NULL, order_line_id VARCHAR(36) NOT NULL, quantity DECIMAL(18,4) NOT NULL,
 received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0, PRIMARY KEY(shipment_id,order_line_id),
 CONSTRAINT fk_purchase_shipment_line_head FOREIGN KEY(shipment_id) REFERENCES purchase_shipment(id) ON DELETE CASCADE,
 CONSTRAINT fk_purchase_shipment_line_order FOREIGN KEY(order_line_id) REFERENCES purchase_order_line(id),
 CONSTRAINT ck_purchase_shipment_quantity CHECK(quantity>0 AND received_quantity>=0 AND received_quantity<=quantity)
);
CREATE INDEX idx_purchase_type_delivery ON purchase_order(order_type,expected_delivery_date,id);
CREATE INDEX idx_purchase_shipment_order ON purchase_shipment(order_id,created_at);
