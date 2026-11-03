-- Convert legacy locks to the single disabled state; never revive old sessions.
UPDATE iam_session SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL AND user_id IN (SELECT id FROM iam_user WHERE locked_at IS NOT NULL);
UPDATE iam_user SET status = 'DISABLED', locked_at = NULL, updated_at = CURRENT_TIMESTAMP
WHERE locked_at IS NOT NULL;
