# Development Guide

Setup, running and testing ReadyBell locally (backend + `dashboard-ui`).

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 21+ | `java -version` |
| Node | 18+ | `node -v` |
| PostgreSQL | 15 | `psql -U postgres` |
| Redis | any | `redis-cli ping` → `PONG` |

## Quick start

```bash
./start-dev.sh            # checks prerequisites, starts PostgreSQL + Redis
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun   # backend → http://localhost:8080
cd dashboard-ui && npm install && npm run dev   # UI → http://localhost:5173
./stop-dev.sh             # stop everything
```

Manual alternative: start PostgreSQL and Redis yourself (Docker, `brew services`, `systemctl`),
then `createdb -U postgres readybell` once.

### Infrastructure notes
- PostgreSQL: the default profile uses `DB_URL` (default `jdbc:postgresql://localhost:5432/readybell`), `DB_USERNAME`, `DB_PASSWORD`
  and `ddl-auto: update`. The **`dev` profile** (`application-dev.yml`) points to port 3254 (e.g. `docker run -p 3254:5432 postgres:15`),
  recreates the schema on every start (`ddl-auto: create`) and defines the local API users — never use it against real data.
- Redis: port 6379. Set `SSE_REDIS_ENABLED=true` when running more than one backend replica so SSE updates reach clients on every node.
- Health check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.
- API users come from `readybell.security.users` (no built-in credentials). `dev` profile: `staff:password` and `admin:admin`
  (tenant `00000000-0000-0000-0000-000000000001`), `platform:platform` (PLATFORM_ADMIN). The tenant is bound to the user on the server;
  an `X-Tenant-Id` header is never trusted (a different tenant in it → 403).
  `curl -u staff:password http://localhost:8080/api/orders` (Basic) or log in for a Bearer token:
  `curl -s -X POST localhost:8080/api/login -H 'Content-Type: application/json' -d '{"username":"staff","password":"password"}'`.
- `JWT_SECRET` (≥ 32 bytes) is **required** outside the `dev`/`test` profiles; the app refuses to start without it.

### Frontend
`npm run dev` talks to the **real backend**: the browser calls the Vite origin and the dev proxy forwards `/api/*` to `BACKEND_URL`
(default `http://localhost:8080`; e.g. `BACKEND_URL=http://localhost:8089 npm run dev` if 8080 is taken). Sign in on `/login` with a configured user
(`staff` / `password` in the `dev` profile). Do **not** set `VITE_API_BASE_URL` in dev: it makes the browser call the backend directly, which fails on CORS
(use it only when the UI is deployed on a different origin than the API and the backend has CORS configured).

The dashboard lists the 100 newest orders and refreshes on SSE events (`/api/orders/stream?ticket=…`, a single-use ticket from `POST /api/orders/stream/ticket`; the badge shows Live/Offline) and every 30 s while offline.
The UI shows the backend's `IN_PROGRESS` as "Preparing".

Staff change an order's status from the pencil menu in the orders table (only the transitions the lifecycle allows are offered; finished orders are disabled);
the change is sent with `PATCH /api/orders/{id}/status` and the list reloads. **Logout** (sidebar and settings menu) clears the stored token and returns to `/login`.

To see data locally: the `dev` profile seeds a restaurant (`dev-restaurant`) for the `staff` / `admin` tenant at startup.
Sign in as `admin` / `admin` and press **New order**, or use the dashboard's *Generate / Import test data* buttons
(`dashboard-ui/test-data/test-orders-50.json` holds 50 sample orders). Details of the API: [API_INTEGRATION.md](API_INTEGRATION.md).

## Tests

Backend tests need **Docker** and nothing else running locally: PostgreSQL 16 and Redis 7 are started by Testcontainers
and removed when the run ends (a local Redis on 6379 is neither needed nor used).
- PostgreSQL: `org.readybell.testsupport.SharedPostgres` (one container per run, `max_connections=300` because every cached
  Spring context keeps its own connection pool). Flyway applies the real migrations and Hibernate only validates the
  entities. `OrderNumberMigrationTest` starts its own container to test a migration against pre-existing data.
- Redis: `org.readybell.testsupport.SharedRedis` (one container per run).
- `TestContainersInitializer`, registered in `src/test/resources/META-INF/spring.factories`, points every Spring test
  context at both containers; no test class has to opt in.

```bash
./gradlew test                                   # backend (JUnit 5 + jqwik)
cd dashboard-ui
npm test                                         # frontend (Vitest)
npm test -- KeyboardNavigation.integration.test.tsx
npm test -- --coverage
npm run build && npm run lint
```

### Manual UI checklist
- **Theme**: toggle sun/moon, reload → choice persists (`localStorage` key `readybell-theme`); first load follows OS preference.
- **Orders table**: pagination (25/page), sorting, status filter, search, date-range filter, status change via dropdown.
- **Real-time**: SSE updates with a 30 s polling fallback while the stream is down. A new order entered on another screen appears live; the header's speaker button (off by default, remembered in `localStorage` key `readybell-alert-sound`) turns on a chime and vibration for it, the tab title shows `(n)` while the tab is in the background, and screen readers get a polite "New order #12" announcement. The screen the order was entered on stays quiet.
- **Auth**: staff/admin login, 401 on invalid token, 403 on cross-tenant access.
- **Keyboard**: Tab order, Enter/Space activation, visible focus rings, skip link.
- **Responsive**: 320 / 768 / 1024 px; no horizontal scroll except the table; touch targets ≥ 44 px.
- **Browsers**: Chrome, Firefox, Safari, Edge; mobile via DevTools device mode (Ctrl+Shift+M).

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Port 8080 busy | `lsof -i :8080`, kill the PID |
| Port 5173 busy | Vite picks the next free port |
| DB missing | `createdb -U postgres readybell` |
| Redis down | `redis-server` |
| Blank page / odd frontend state | check console (F12); `rm -rf node_modules .vite && npm install` |
| UI can't reach backend | check `/actuator/health` and the proxy in `vite.config.ts` |

## Repository layout

```
src/main/java/org/readybell/   session, ordering, subscription, restaurant, notification, platformadmin, platform
src/test/java/                 backend tests
dashboard-ui/src/              components, pages, hooks, context, types, utils
docs/                          architecture, brand, product + this guide, API_INTEGRATION, UI_QUALITY, PROGRESS
.kiro/specs/                   requirements / design / tasks per feature
```

## Opening a tracking link on a phone (QR scan)

The QR code contains the address the dashboard was opened with. With `http://localhost:5173` the phone would
look for itself, so Safari says "can't connect to the server". To test with a real phone:

1. Phone and computer on the same Wi-Fi.
2. Start the UI reachable from the network: `cd dashboard-ui && npm run dev:lan`
   (use `BACKEND_URL=http://localhost:8081 npm run dev:lan` if the backend is not on 8080).
3. Open the dashboard on the computer through its network address, e.g. `http://192.168.x.x:5173`
   (or set `VITE_PUBLIC_URL=http://192.168.x.x:5173` and keep using localhost), then create a **new** order:
   old QR codes keep their old address.
4. Scan the QR code; the tracking page opens and updates live.

`dev:lan` exposes the dev server (and, through its proxy, the dev backend with its `admin/admin` seed users)
to everyone on the network, so use it only on a network you trust.

**Notifications need HTTPS.** Browsers allow service workers and Web Push only on HTTPS (or localhost), so on
`http://192.168…` the page tracks the order but "Notify me" is unavailable. To try push on a phone, serve the
app over HTTPS, e.g. through a tunnel (`cloudflared tunnel --url http://localhost:5173` or `ngrok http 5173`) and
use that https address as `VITE_PUBLIC_URL`; iPhones additionally need the page added to the Home Screen.
Real VAPID keys are required too (`python3 scripts/generate-vapid-keys.py`).


## Browser E2E tests (Playwright)

`dashboard-ui/e2e/` drives a real Chromium against the real stack (Spring Boot in the `dev` profile + the Vite dev server
that proxies `/api` + PostgreSQL). Scenarios: staff sign-in (and the login guard), the main flow (staff create an order,
the receipt QR link opens in a second browser, the customer's open page follows *Received -> Being prepared -> Ready*
live over SSE), server-side paging of Incoming Orders with 25 generated orders, and the dark theme.

```bash
# once: a database that is used for nothing else - the dev profile rebuilds its schema on every start
docker exec readybell-pg psql -U postgres -c "CREATE DATABASE readybell_e2e"    # or: createdb readybell_e2e
cd dashboard-ui
npm ci --legacy-peer-deps
npx playwright install chromium                                                  # once
npm run e2e                                                                      # starts both servers itself
npm run e2e:ui                                                                   # Playwright UI mode
```

Playwright starts the backend on port 18080 and Vite on 5273, so a normal dev stack on 8080/5173 is untouched. Servers that
are already running on those ports are reused (outside CI), which is quick but means a stale server is tested; stop it if
a run behaves oddly. Connection settings: `E2E_DB_URL` (default `jdbc:postgresql://localhost:3254/readybell_e2e`),
`E2E_DB_USERNAME`, `E2E_DB_PASSWORD`, `E2E_BACKEND_PORT`, `E2E_UI_PORT`. **Never point `E2E_DB_URL` at the database of your
dev stack.** Failed tests keep a trace and a video in `dashboard-ui/test-results/` (`npx playwright show-trace <trace.zip>`);
CI uploads them, with the HTML report, as the `e2e-report` artifact.
