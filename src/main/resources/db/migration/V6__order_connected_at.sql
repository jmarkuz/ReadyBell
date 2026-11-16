-- The counter claim by receipt number is gone; the column now records when the customer first opened the tracking link
-- (scanned the QR), which the staff screen shows as "connected".
ALTER TABLE "order" RENAME COLUMN claimed_at TO connected_at;
