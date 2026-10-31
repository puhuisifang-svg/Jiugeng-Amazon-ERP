CREATE TABLE iam_user_defaults (
    id INTEGER NOT NULL PRIMARY KEY,
    encrypted_password TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO iam_user_defaults (id, encrypted_password) VALUES (1, '');
