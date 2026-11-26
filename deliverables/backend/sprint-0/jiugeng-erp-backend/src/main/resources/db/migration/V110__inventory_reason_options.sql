CREATE TABLE inventory_reason_hidden (
    name VARCHAR(500) NOT NULL PRIMARY KEY
);

CREATE INDEX idx_purchase_receipt_inventory ON purchase_receipt_line(inventory_transaction_id);
