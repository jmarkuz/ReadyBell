#!/usr/bin/env bash
# Seeds N orders for a scratch tenant, prints EXPLAIN ANALYZE for the order-list query, then removes the rows.
# Usage: DB_URL=postgresql://user:pass@localhost:3254/readybell scripts/explain-order-list.sh [N=100000]
# Dev databases only.
set -euo pipefail
N="${1:-100000}"
: "${DB_URL:?set DB_URL, e.g. postgresql://readybell:secret@localhost:3254/readybell}"
TENANT="ffffffff-ffff-ffff-ffff-ffffffffffff"

cleanup() {
  psql "$DB_URL" -q -c "DELETE FROM order_item WHERE order_id IN (SELECT id FROM \"order\" WHERE tenant_id = '$TENANT')" \
                 -c "DELETE FROM \"order\" WHERE tenant_id = '$TENANT'"
}
trap cleanup EXIT

psql "$DB_URL" -q -v ON_ERROR_STOP=1 <<SQL
INSERT INTO "order" (id, tenant_id, session_id, status, version, created_at, updated_at)
SELECT gen_random_uuid(), '$TENANT', gen_random_uuid(),
       (ARRAY['CREATED','IN_PROGRESS','READY','COMPLETED','CANCELLED'])[1 + (g % 5)],
       0, now() - (g || ' seconds')::interval, now()
FROM generate_series(1, $N) g;
INSERT INTO order_item (order_id, "position", name, quantity)
SELECT id, 0, 'Item', 1 FROM "order" WHERE tenant_id = '$TENANT';
ANALYZE "order"; ANALYZE order_item;
SQL

echo "== list: tenant + status, newest first, page of 100 =="
psql "$DB_URL" -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM \"order\" WHERE tenant_id = '$TENANT' AND status = 'READY' ORDER BY created_at DESC LIMIT 100"
echo "== list: tenant only, newest first =="
psql "$DB_URL" -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM \"order\" WHERE tenant_id = '$TENANT' ORDER BY created_at DESC LIMIT 100"
echo "== items of one order =="
psql "$DB_URL" -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM order_item WHERE order_id = (SELECT id FROM \"order\" WHERE tenant_id = '$TENANT' LIMIT 1)"
