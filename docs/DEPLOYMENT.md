# Deployment

ReadyBell ships as **one container**: the Spring Boot API plus the built dashboard / customer PWA
(served from the same origin, so no CORS and the service worker works). PostgreSQL and Redis run beside it.

## Quick start (docker compose)

```bash
cp .env.example .env        # then fill in every value, see below
docker compose up --build   # app on http://localhost:8080 (APP_PORT to change)
```

| Variable | What |
|---|---|
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | HS256 key, at least 32 characters (`openssl rand -base64 48`). The app refuses to start without it |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | Web Push keys: `python3 scripts/generate-vapid-keys.py`. **Keep the pair stable**, changing it invalidates every customer subscription. With the placeholders push is off and the customer page hides "Notify me" |
| `PLATFORM_ADMIN_USERNAME` / `PLATFORM_ADMIN_PASSWORD_HASH` | The platform operator. The hash is `{bcrypt}…` with every `$` doubled (`$$`), see `.env.example` |
| `CORS_ALLOWED_ORIGINS` | Only if the UI is hosted on another origin |

Put it behind HTTPS (a reverse proxy or load balancer): browsers only allow service workers and Web Push on
HTTPS (localhost excepted), and iPhones need the page added to the Home Screen.

## First run

1. Sign in as the platform operator, open **Restaurants**, add a restaurant together with its first admin.
2. That admin signs in, adds staff under **Staff**, and can rename the restaurant under **Settings**.
3. Staff press **New order**: the dialog shows a QR code (print it on the receipt). Customers scan it,
   follow the order and tap **Notify me**.

Check a running instance end to end with `PLATFORM_PASS=… BASE_URL=… scripts/smoke.sh`.
Before the first pilot restaurant, work through [PILOT_CHECKLIST.md](PILOT_CHECKLIST.md) (real-device Web Push tests, timing sheet, run sheet).

## Operations

- **Database**: Flyway owns the schema (`src/main/resources/db/migration`), Hibernate only validates it.
  A database that predates Flyway is adopted automatically at V1. Add changes as new `V<n>__*.sql` files,
  never edit an applied one. Backups: see [Backup & restore](#backup--restore).
- **Several replicas**: set `SSE_REDIS_ENABLED=true` (live updates reach every node) and
  `LOGIN_REDIS_ENABLED=true` (one shared failed-login limit); compose does both.
- **Probes**: `/actuator/health/liveness` and `/actuator/health/readiness`.
- **Metrics**: `/actuator/prometheus`, readable only by the platform operator (HTTP Basic works for scrapers).
  Business metrics, alert rules and a Prometheus setup: [MONITORING.md](MONITORING.md).
- **Housekeeping**: hourly, sessions older than `readybell.session.ttl` (24h) with no open orders are ended and
  their push subscriptions deleted. Orders are kept.
- **Logs**: unexpected errors are logged with a stack trace; clients only see a generic 500.

## Local development

`start-dev.sh` / the `dev` profile (`SPRING_PROFILES_ACTIVE=dev`) recreate the schema on every start and seed
`staff`, `admin` and `platform` users (see `application-dev.yml`). The dev profile also exposes
`/api/dev/test-data` (generate / import / delete sample orders) behind the dashboard's test-data buttons.

## Backup & restore

PostgreSQL holds all state (Redis only carries live-update fan-out and login throttling, so it needs no backup).
Scripts live in `scripts/` and need `pg_dump`/`pg_restore`/`psql` 16 (the compose `backup` service has them).

| Script | Purpose |
|---|---|
| `backup.sh` | `pg_dump -Fc` to `$BACKUP_DIR/<db>_<UTC timestamp>.dump`, verifies the archive, prunes dumps older than `RETENTION_DAYS` (default 14). Non-zero exit on failure. |
| `restore.sh DUMP [--target-db NAME] [--force-target-live]` | Restores into a **new scratch database** by default. Refuses the live database unless `--force-target-live` is given. |
| `backup-drill.sh` | backup, restore into a scratch DB, compare row counts of every table, drop the scratch DB. Exit 0 = pass. |

Connection settings are the standard `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`; other settings are
`BACKUP_DIR` (default `./backups`) and `RETENTION_DAYS`. Secrets are never printed.

### Schedule

- **Compose**: `docker compose --profile backup up -d backup` runs `backup.sh` every `BACKUP_INTERVAL_HOURS`
  (default 24) into `./backups`. A plain `docker compose up` does not start it.
- **Host cron** (alternative), daily at 03:15 with the compose stack running:
  `15 3 * * * cd /opt/readybell && docker compose --profile backup run --rm backup /scripts/backup.sh >>backups/cron.log 2>&1`
- One-off: `docker compose --profile backup run --rm backup /scripts/backup.sh`.
- On a host with a PostgreSQL client: `PGHOST=… PGPASSWORD=… BACKUP_DIR=/var/backups/readybell scripts/backup.sh`.

**Copy dumps off the host.** `./backups` on the same disk as the database does not survive losing the machine.
Sync it to other storage (object storage, another server) after each run, e.g. `rclone sync backups remote:readybell-backups`,
and keep that copy access-controlled: dumps contain password hashes and push-subscription keys.

### Restore

1. Pick a dump (`ls -t backups | head`), ideally copied back from the off-host store.
2. Inspect first by restoring into a scratch database (safe, the live one is untouched):
   `docker compose --profile backup run --rm backup /scripts/restore.sh /backups/readybell_<ts>.dump`
   and check it with `psql`.
3. To replace the live data: `docker compose stop app`, then
   `docker compose --profile backup run --rm backup /scripts/restore.sh /backups/readybell_<ts>.dump --target-db readybell --force-target-live`,
   then `docker compose start app` (Flyway migrates forward if the dump predates the running version).
   Drop the scratch database afterwards: `docker compose exec postgres psql -U readybell -d postgres -c 'DROP DATABASE "<name>"'`.
4. Check `/actuator/health/readiness` and run `scripts/smoke.sh`.

### Expectations

- **RPO** (data you can lose) is up to one backup interval: 24h with the defaults. Lower `BACKUP_INTERVAL_HOURS`
  for a tighter RPO; point-in-time recovery (WAL archiving) is not set up.
- **RTO** (time to recover) is dominated by getting the dump back plus `pg_restore`: minutes for a database of this
  size (the drill prints its own timing; measure with your real data).
- Retention is 14 days locally; keep the off-host copy as long as you need.

### Restore drill

Run monthly, and after any change to backup settings or major schema changes:

```
docker compose --profile backup run --rm backup /scripts/backup-drill.sh
```

It exits non-zero on any row-count mismatch or error and always drops its scratch database. Log each run:

| Date | Who | Dump size | Drill result (PASS/FAIL) | Time | Notes |
|---|---|---|---|---|---|
| | | | | | |

Checklist: drill passed; latest off-host copy exists and is recent; a dump from the off-host store (not the local one)
restores; `RETENTION_DAYS` and free disk are adequate; backup service logs show no `BACKUP FAILED`.
