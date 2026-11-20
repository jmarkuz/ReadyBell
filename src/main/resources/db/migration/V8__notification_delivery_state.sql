-- Push delivery without sleeping threads: a notification is a delivery task. It carries what a retry needs (title,
-- click target), when it is due, how many attempts were made, its deadline and the lease of the worker holding it.
ALTER TABLE notification
    ADD COLUMN title varchar(255),
    ADD COLUMN url varchar(512),
    ADD COLUMN ready_at timestamp(6) with time zone,
    ADD COLUMN attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at timestamp(6) with time zone,
    ADD COLUMN expires_at timestamp(6) with time zone,
    ADD COLUMN locked_until timestamp(6) with time zone,
    ADD COLUMN last_error varchar(255);

-- the worker's claim query: pending and due, oldest first (rows stored before this migration have no next_attempt_at
-- and are never picked up: a push that was left half-done by an old process is not worth sending now)
CREATE INDEX idx_notification_due ON notification (next_attempt_at)
    WHERE status = 'PENDING' AND next_attempt_at IS NOT NULL;
