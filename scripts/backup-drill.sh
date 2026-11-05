#!/usr/bin/env bash
# Restore drill: backup -> restore into a scratch DB -> compare row counts -> drop scratch DB.
# Exit 0 only if every table has identical counts. Uses the same PG* env as backup.sh.
# Optional: BACKUP_DIR (default: a temp dir, removed afterwards), KEEP_SCRATCH=1 keeps the scratch DB.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-readybell}"
export PGUSER="${PGUSER:-readybell}"
TABLES=(restaurant staff_account customer_session device_subscription '"order"' order_item notification outbox_event flyway_schema_history)

log() { echo "[drill] $*" >&2; }
count() { psql -d "$1" -XAtq -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM $2"; }

own_dir=0
if [ -z "${BACKUP_DIR:-}" ]; then BACKUP_DIR="$(mktemp -d)"; own_dir=1; fi
export BACKUP_DIR
scratch=""
cleanup() {
  if [ -n "$scratch" ] && [ "${KEEP_SCRATCH:-0}" != 1 ]; then
    if psql -d postgres -Xq -c "DROP DATABASE IF EXISTS \"$scratch\"" >/dev/null 2>&1; then
      log "dropped $scratch"
    else
      log "WARN: could not drop $scratch"
    fi
  fi
  if [ "$own_dir" -eq 1 ]; then rm -rf "$BACKUP_DIR"; fi
  return 0
}
trap cleanup EXIT

start=$(date +%s)
dump="$(RETENTION_DAYS=0 "$here/backup.sh")"
scratch="$("$here/restore.sh" "$dump" --target-db "${PGDATABASE}_drill_$(date -u +%Y%m%d%H%M%S)")"

fail=0
printf '%-24s %10s %10s\n' table live restored >&2
for t in "${TABLES[@]}"; do
  a="$(count "$PGDATABASE" "$t")"
  b="$(count "$scratch" "$t")"
  mark=ok
  if [ "$a" != "$b" ]; then mark=MISMATCH; fail=1; fi
  printf '%-24s %10s %10s  %s\n' "${t//\"/}" "$a" "$b" "$mark" >&2
done
elapsed=$(( $(date +%s) - start ))
if [ "$fail" -eq 0 ]; then log "PASS in ${elapsed}s"; else log "FAIL (row count mismatch)"; fi
exit "$fail"
