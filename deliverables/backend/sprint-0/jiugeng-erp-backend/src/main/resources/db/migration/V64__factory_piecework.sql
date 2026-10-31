CREATE TABLE factory_lock (id INT PRIMARY KEY);
INSERT INTO factory_lock(id) VALUES (1);
CREATE TABLE factory_record (
 id VARCHAR(64) PRIMARY KEY, kind VARCHAR(24) NOT NULL, employee_id VARCHAR(64) NOT NULL DEFAULT '',
 date_key VARCHAR(10) NOT NULL DEFAULT '', status VARCHAR(24) NOT NULL, search_text VARCHAR(2000) NOT NULL,
 amount DECIMAL(18,2) NOT NULL DEFAULT 0, document_json LONGTEXT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_factory_list ON factory_record(kind,date_key,status);
CREATE INDEX idx_factory_employee ON factory_record(kind,employee_id,date_key);
CREATE TABLE factory_event (id VARCHAR(64) PRIMARY KEY, record_id VARCHAR(64) NOT NULL, action VARCHAR(60) NOT NULL,
 actor_name VARCHAR(128) NOT NULL, reason VARCHAR(1000) NOT NULL, before_json LONGTEXT NOT NULL, after_json LONGTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_factory_history ON factory_event(record_id,created_at);
CREATE TABLE factory_request (request_id VARCHAR(64) PRIMARY KEY, actor_id VARCHAR(64) NOT NULL, request_json LONGTEXT NOT NULL, response_json LONGTEXT NOT NULL);
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.employees.read');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.employees.write');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.rates.read');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.rates.write');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.reports.read');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.reports.write');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.reports.review');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.payroll.read');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.payroll.write');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.payroll.lock');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.payroll.correct');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.payroll.pay');
INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('ADMIN','factory.payroll.export');
CREATE TABLE factory_attachment (
 id VARCHAR(64) PRIMARY KEY, record_id VARCHAR(64) NOT NULL, filename VARCHAR(200) NOT NULL,
 media_type VARCHAR(60) NOT NULL, content_hash VARCHAR(64) NOT NULL, content_bytes LONGBLOB NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(record_id) REFERENCES factory_record(id), UNIQUE(record_id,content_hash)
);
