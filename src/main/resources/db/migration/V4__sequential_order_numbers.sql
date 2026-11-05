-- Per-restaurant sequential order numbers (#1, #2, #3 ...; never reset).
-- Written in the SQL subset shared by PostgreSQL and H2 so the backfill can be unit-tested.

ALTER TABLE "order" ADD COLUMN order_number integer;

-- Backfill existing orders oldest-first within each restaurant (id breaks created_at ties deterministically).
CREATE TABLE order_number_backfill AS
SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS rn
FROM "order";
CREATE UNIQUE INDEX uq_order_number_backfill_id ON order_number_backfill (id);

UPDATE "order"
SET order_number = (SELECT b.rn FROM order_number_backfill b WHERE b.id = "order".id);

DROP TABLE order_number_backfill;

ALTER TABLE "order" ALTER COLUMN order_number SET NOT NULL;
ALTER TABLE "order" ADD CONSTRAINT uq_order_tenant_number UNIQUE (tenant_id, order_number);

-- One counter row per restaurant; the next number is allocated by a row-locking UPDATE in the order's transaction.
CREATE TABLE order_counter (
    tenant_id uuid NOT NULL,
    last_number integer NOT NULL,
    CONSTRAINT order_counter_pkey PRIMARY KEY (tenant_id)
);

INSERT INTO order_counter (tenant_id, last_number)
SELECT tenant_id, MAX(order_number) FROM "order" GROUP BY tenant_id;
