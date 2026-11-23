CREATE TABLE supplier_write_request (
 actor_id VARCHAR(36) NOT NULL,
 request_id VARCHAR(100) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 supplier_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(actor_id, request_id)
);
