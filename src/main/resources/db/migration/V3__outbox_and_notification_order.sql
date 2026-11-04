-- Transactional outbox: domain events are written in the same transaction as the change that caused them
-- and delivered at least once by OutboxRelay.
CREATE TABLE outbox_event (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    type character varying(255) NOT NULL,
    payload text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    next_attempt_at timestamp(6) with time zone NOT NULL,
    locked_until timestamp(6) with time zone,
    processed_at timestamp(6) with time zone,
    failed_at timestamp(6) with time zone,
    attempts integer NOT NULL,
    last_error text,
    CONSTRAINT outbox_event_pkey PRIMARY KEY (id)
);

-- the relay's claim query: oldest unprocessed, due, not failed
CREATE INDEX idx_outbox_pending ON outbox_event (next_attempt_at)
    WHERE processed_at IS NULL AND failed_at IS NULL;

-- which order a notification was sent for, so a replayed OrderReady does not push twice
ALTER TABLE notification ADD COLUMN order_id uuid;
CREATE INDEX idx_notification_order ON notification (tenant_id, order_id, subscription_id);
