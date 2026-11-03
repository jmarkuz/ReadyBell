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
./gradlew bootRun         # backend  → http://localhost:8080
cd dashboard-ui && npm install && npm run dev   # UI → http://localhost:5173
./stop-dev.sh             # stop everything
```

Manual alternative: start PostgreSQL and Redis yourself (Docker, `brew services`, `systemctl`),
then `createdb -U postgres readybell` once.

### Infrastructure notes
- PostgreSQL: `application.yml` defaults to port **3254** (e.g. `docker run -p 3254:5432 postgres:15`). If you use the
  standard 5432, change `spring.datasource.url` to `jdbc:postgresql://localhost:5432/readybell`.
- Redis: port 6379.
- Health check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.
- Staff Basic Auth for quick curl tests: `staff:password`
  (`curl -H "Authorization: Basic c3RhZmY6cGFzc3dvcmQ=" http://localhost:8080/api/orders`).

### Frontend with mock vs. real backend
In dev the UI starts MSW mocks automatically (see [API_INTEGRATION.md](API_INTEGRATION.md)); to use the real backend
disable the `worker.start()` block in `dashboard-ui/src/main.tsx`. `VITE_API_BASE_URL` sets the backend URL.

If MSW fails with "Failed to register the Service Worker", make sure
`dashboard-ui/public/mockServiceWorker.js` exists (`npx msw init public/`).

## Tests

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
- **Real-time**: SSE updates (~3 s in mock) with polling fallback.
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
dashboard-ui/src/              components, pages, hooks, context, mocks (MSW), types, utils
docs/                          architecture, brand, product + this guide, API_INTEGRATION, UI_QUALITY, PROGRESS
.kiro/specs/                   requirements / design / tasks per feature
```
