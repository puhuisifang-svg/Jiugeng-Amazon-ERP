-- Initial passwords are usable immediately; clear outstanding legacy requirements.
-- Future explicit administrator password resets still require replacing the temporary password.
ALTER TABLE iam_user ALTER COLUMN must_change_password SET DEFAULT FALSE;
UPDATE iam_user SET must_change_password = FALSE WHERE must_change_password = TRUE;
