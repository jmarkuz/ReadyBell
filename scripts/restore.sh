#!/usr/bin/env bash
# Restore a pg_dump -Fc file. By default into a NEW scratch database; never touches the live one
# unless --force-target-live is given.
#
# Usage: restore.sh DUMP_FILE [--target-db NAME] [--force-target-live]
# Config (env): PGHOST PGPORT PGUSER PGPASSWORD, PGDATABASE (= the live database, default readybell).
# Default target: ${PGDATABASE}_restore_<timestamp>. Needs CREATEDB (the DB owner role in compose has it).
# Prints the name of the restored database on stdout.
set -euo pipefail

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-readybell}"
LIVE_DB="${PGDATABASE:-readybell}"
unset PGDATABASE

log() { echo "[restore] $*" >&2; }
usage() { echo "Usage: $0 DUMP_FILE [--target-db NAME] [--force-target-live]" >&2; exit 2; }

dump="" target="" force=0
while [ $# -gt 0 ]; do
  case "$1" in
    --target-db) [ $# -ge 2 ] || usage; target="$2"; shift 2;;
    --force-target-live) force=1; shift;;
    -h|--help) usage;;
    -*) usage;;
    *) [ -z "$dump" ] || usage; dump="$1"; shift;;
  esac
done
[ -n "$dump" ] && [ -r "$dump" ] || { log "dump file missing or unreadable"; usage; }
target="${target:-${LIVE_DB}_restore_$(date -u +%Y%m%dT%H%M%SZ)}"
case "$target" in *[!A-Za-z0-9_]*|'') log "target db name must match [A-Za-z0-9_]+"; exit 2;; esac

if [ "$target" = "$LIVE_DB" ] && [ "$force" -ne 1 ]; then
  log "refusing to restore into the live database '$LIVE_DB' without --force-target-live"
  exit 2
fi

log "dump:   $dump"
log "server: ${PGUSER}@${PGHOST}:${PGPORT}"
if [ "$target" = "$LIVE_DB" ]; then
  log "target: $target (LIVE) - existing objects will be DROPPED and replaced. Stop the app first."
  pg_restore --exit-on-error --clean --if-exists --no-owner --no-privileges -d "$target" "$dump"
else
  log "target: $target (new scratch database)"
  psql -v ON_ERROR_STOP=1 -d postgres -qc "CREATE DATABASE \"$target\""
  if ! pg_restore --exit-on-error --no-owner --no-privileges -d "$target" "$dump"; then
    log "restore failed; scratch database '$target' left in place for inspection"
    exit 1
  fi
fi
log "done: restored into '$target'"
echo "$target"
