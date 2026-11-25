-- What a push is about: READY (the default, every push so far) or CANCELLED. A replayed event must not queue a second push of
-- the same kind, but an order that was READY and is then cancelled does get the cancellation push.
ALTER TABLE notification ADD COLUMN kind varchar(16) NOT NULL DEFAULT 'READY';
