-- Counter QR flow: a customer claims an order by typing its number; each order can be claimed once.
ALTER TABLE "order" ADD COLUMN claimed_at timestamp(6) with time zone;
