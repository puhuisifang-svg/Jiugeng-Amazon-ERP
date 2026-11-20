-- Customer-based bills and their account history are retained.
DELETE FROM finance_attachment WHERE bill_id IN (SELECT id FROM finance_bill WHERE customer_id IS NULL);
DELETE FROM finance_bill WHERE customer_id IS NULL;
UPDATE finance_bill SET shop_id = NULL WHERE customer_id IS NOT NULL;
DELETE FROM finance_account;
DELETE FROM finance_shop;
