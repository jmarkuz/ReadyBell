-- When an account was deactivated: the retention run anonymizes the username of accounts deactivated long ago
ALTER TABLE staff_account ADD COLUMN deactivated_at timestamp(6) with time zone;
UPDATE staff_account SET deactivated_at = now() WHERE status = 'DEACTIVATED';
