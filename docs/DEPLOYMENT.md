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

## Operations

- **Database**: Flyway owns the schema (`src/main/resources/db/migration`), Hibernate only validates it.
  A database that predates Flyway is adopted automatically at V1. Add changes as new `V<n>__*.sql` files,
  never edit an applied one. Back up PostgreSQL yourself (e.g. `pg_dump` on a schedule).
- **Several replicas**: set `SSE_REDIS_ENABLED=true` (live updates reach every node) and
  `LOGIN_REDIS_ENABLED=true` (one shared failed-login limit); compose does both.
- **Probes**: `/actuator/health/liveness` and `/actuator/health/readiness`.
- **Metrics**: `/actuator/prometheus`, readable only by the platform operator (HTTP Basic works for scrapers).
- **Housekeeping**: hourly, sessions older than `readybell.session.ttl` (24h) with no open orders are ended and
  their push subscriptions deleted. Orders are kept.
- **Logs**: unexpected errors are logged with a stack trace; clients only see a generic 500.

## Local development

`start-dev.sh` / the `dev` profile (`SPRING_PROFILES_ACTIVE=dev`) recreate the schema on every start and seed
`staff`, `admin` and `platform` users (see `application-dev.yml`). The dev profile also exposes
`/api/dev/test-data` (generate / import / delete sample orders) behind the dashboard's test-data buttons.
