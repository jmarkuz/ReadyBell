#!/usr/bin/env bash
# PostgreSQL backup (pg_dump custom format) with retention pruning.
#
# Config (env, libpq style): PGHOST (localhost) PGPORT (5432) PGDATABASE (readybell)
#   PGUSER (readybell) PGPASSWORD (required unless ~/.pgpass / trust auth),
#   BACKUP_DIR (./backups), RETENTION_DAYS (14; 0 disables pruning).
# Prints the path of the created dump on stdout; logs go to stderr. Exit != 0 on failure.
#
# In the compose network:  docker compose --profile backup run --rm backup /scripts/backup.sh
set -euo pipefail

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-readybell}"
export PGUSER="${PGUSER:-readybell}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

log() { echo "[backup] $*" >&2; }

case "$RETENTION_DAYS" in ''|*[!0-9]*) log "RETENTION_DAYS must be a non-negative integer"; exit 2;; esac
mkdir -p "$BACKUP_DIR"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$BACKUP_DIR/${PGDATABASE}_${stamp}.dump"
tmp="$file.partial"
trap 'rm -f "$tmp"' EXIT

log "dumping ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE} -> $file"
pg_dump -Fc --no-owner --no-privileges -f "$tmp"
[ -s "$tmp" ] || { log "dump is empty"; exit 1; }
pg_restore --list "$tmp" >/dev/null   # sanity check: the archive must be readable
mv "$tmp" "$file"
log "ok ($(du -h "$file" | cut -f1))"

if [ "$RETENTION_DAYS" -gt 0 ]; then
  find "$BACKUP_DIR" -maxdepth 1 -type f -name "${PGDATABASE}_*.dump" -mtime "+$RETENTION_DAYS" \
    -print -delete | sed 's/^/[backup] pruned /' >&2
fi
echo "$file"
