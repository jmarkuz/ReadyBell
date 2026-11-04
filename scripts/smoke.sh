#!/usr/bin/env bash
# End-to-end smoke test of the core flow against a running ReadyBell:
#   platform operator -> new restaurant (+admin) -> order entry (receipt link) -> customer tracking -> READY
#
#   BASE_URL=http://localhost:8080 PLATFORM_USER=platform PLATFORM_PASS=... scripts/smoke.sh
#
# Creates a throwaway restaurant "smoke-<random>"; it can be deactivated afterwards in the UI.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PLATFORM_USER="${PLATFORM_USER:-platform}"
PLATFORM_PASS="${PLATFORM_PASS:?set PLATFORM_PASS}"
ID="smoke-$(head -c4 /dev/urandom | od -An -tx1 | tr -d ' \n')"
ADMIN="admin-$ID"
ADMIN_PASS="$(head -c12 /dev/urandom | base64 | tr -d '/+=')A1"

J='Content-Type: application/json'
fail() { echo "FAIL: $*" >&2; exit 1; }
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
step() { echo "• $*"; }

step "health"
curl -fsS "$BASE_URL/actuator/health" | grep -q UP || fail "not healthy"

step "platform operator signs in"
PT=$(curl -fsS -X POST "$BASE_URL/api/login" -H "$J" -d "{\"username\":\"$PLATFORM_USER\",\"password\":\"$PLATFORM_PASS\"}" | json "d['token']")

step "onboard restaurant $ID with its admin"
curl -fsS -X POST "$BASE_URL/api/platform/restaurants" -H "$J" -H "Authorization: Bearer $PT" \
  -d "{\"identifier\":\"$ID\",\"name\":\"Smoke $ID\",\"adminUsername\":\"$ADMIN\",\"adminPassword\":\"$ADMIN_PASS\"}" >/dev/null

step "restaurant admin signs in and sees the restaurant"
AT=$(curl -fsS -X POST "$BASE_URL/api/login" -H "$J" -d "{\"username\":\"$ADMIN\",\"password\":\"$ADMIN_PASS\"}" | json "d['token']")
NAME=$(curl -fsS "$BASE_URL/api/restaurant" -H "Authorization: Bearer $AT" | json "d['name']")
[ "$NAME" = "Smoke $ID" ] || fail "unexpected restaurant name: $NAME"

step "staff enters an order (the server starts the customer session)"
ORDER=$(curl -fsS -X POST "$BASE_URL/api/orders" -H "$J" -H "Authorization: Bearer $AT" \
  -d '{"customerName":"Smoke","estimatedMinutes":5,"items":[{"name":"Latte","quantity":2}]}')
SESSION=$(echo "$ORDER" | json "d['sessionId']")
ORDER_ID=$(echo "$ORDER" | json "d['id']")

step "customer opens the receipt link (no credentials)"
STATUS=$(curl -fsS "$BASE_URL/api/sessions/$SESSION/track" | json "d['orders'][0]['status']")
[ "$STATUS" = "CREATED" ] || fail "expected CREATED, got $STATUS"

step "staff moves the order to READY"
for s in IN_PROGRESS READY; do
  curl -fsS -X PATCH "$BASE_URL/api/orders/$ORDER_ID/status" -H "$J" -H "Authorization: Bearer $AT" -d "{\"status\":\"$s\"}" >/dev/null
done

step "customer sees READY"
STATUS=$(curl -fsS "$BASE_URL/api/sessions/$SESSION/track" | json "d['orders'][0]['status']")
[ "$STATUS" = "READY" ] || fail "expected READY, got $STATUS"

step "web push configuration is published"
curl -fsS "$BASE_URL/api/push/config" | json "'enabled=' + str(d['enabled'])"

echo "OK: smoke test passed ($ID)"
