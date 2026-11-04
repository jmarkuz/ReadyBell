-- Incoming Orders list: filter by tenant (+ status), newest first.
CREATE INDEX idx_order_tenant_created ON "order" USING btree (tenant_id, created_at DESC);
CREATE INDEX idx_order_tenant_status_created ON "order" USING btree (tenant_id, status, created_at DESC);

-- Look up items by order (the primary key leads with "position", so it cannot serve this).
CREATE INDEX idx_order_item_order_id ON order_item USING btree (order_id);
