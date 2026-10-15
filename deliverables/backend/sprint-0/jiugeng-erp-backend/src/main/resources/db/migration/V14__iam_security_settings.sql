CREATE TABLE iam_security_setting (
    setting_key VARCHAR(64) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_by VARCHAR(36) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO iam_security_setting (setting_key, setting_value)
VALUES ('stay_signed_in_days', '7');
