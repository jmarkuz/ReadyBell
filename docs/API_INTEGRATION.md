# Dashboard ↔ Backend API Integration

Source spec: `.kiro/specs/restaurant-notification-platform/` and `.kiro/specs/backend-endpoints-integration/`.
Client code: `dashboard-ui/src/utils/api.ts`, mocks in `dashboard-ui/src/mocks/`, types in `dashboard-ui/src/types/`.

## Endpoints (16 HTTP endpoints + 5 auth helpers in the client, all with MSW mocks)

**Public**: `POST /api/login` (returns `{token, role, tenantId, expiresIn}`), `POST /api/sessions`,
`GET /api/sessions/{id}/orders`, `POST /api/sessions/{id}/subscriptions`, `DELETE /api/sessions/{id}/subscriptions/{subId}`.

**Order management (Bearer, STAFF)**

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/orders` | `page`, `pageSize` (≤100, default 25), `status` (comma list), `sortBy` (orderId/status/eta/customerName), `sortOrder`, `search` |
| POST | `/api/orders` | session must exist and belong to the tenant → `{id, orderId, status, createdAt}` |
| PATCH | `/api/orders/{id}/status` | validated lifecycle transitions → `{id, status, updatedAt}` |
| GET | `/api/orders/stream` | SSE: `OrderCreated`, `OrderStatusUpdated`, `OrderReady` (client `setupSSE`) |
| GET | `/api/orders/poll` | `since` / `eTag`; 200 `{orders[], eTag}` or 304 (client `pollOrders`) |

**Restaurant admin (Bearer, ADMIN)**: `POST /api/admin/staff` (create staff account), `DELETE /api/admin/staff/{accountId}` (deactivate),
`PUT /api/admin/config`. The staff/config calls are stubs in the mock.

**Platform admin (Bearer, PLATFORM_ADMIN, cross-tenant)**: `POST /api/platform/restaurants`,
`PUT /api/platform/restaurants/{tenantId}`, `DELETE /api/platform/restaurants/{tenantId}`.

**Auth helpers (client only)**: `logout()`, `isAuthenticated()`, `getUserProfile()`, `getTenantId()`, `getUserRole()`.

Also in the backend (see [README](../README.md#api-overview)): `GET /api/sessions/{id}/orders/stream` (customer SSE) and the backend Basic-auth test user `staff:password`.

Order lifecycle: `CREATED → IN_PROGRESS → READY → COMPLETED`; `CANCELLED` allowed from any non-terminal state;
`COMPLETED` and `CANCELLED` are terminal.

## Auth & multi-tenancy
- Backend (implemented): `POST /api/login` → `{token, role, tenantId, username, expiresIn}`; `POST /api/auth/refresh` (Bearer; accepts a token
  expired up to `readybell.security.jwt.refresh-grace`, default 7 d, and re-checks the user). HS256 JWT, claims `sub`, `iat`, `exp`, `role`, `roles`,
  `tenant_id` (empty for PLATFORM_ADMIN); TTL `readybell.security.jwt.ttl` (1 h); key from `JWT_SECRET`. HTTP Basic is still accepted for curl/tests.
- Failed logins are throttled per username and per client address (`readybell.security.jwt.login.max-failures` = 5 per `window` = 15 m, in memory per node);
  the 6th attempt gets 429 with `Retry-After`.
- Client behaviour (all tested):
  - request interceptor injects the Bearer header;
  - response interceptor handles 401 with a token refresh and request retry;
  - refresh uses up to 3 attempts with exponential backoff (1 s, 2 s, 4 s) and a 10 s timeout per attempt;
    400/401/403 are permanent failures (no retry), 5xx / network / timeout are retried;
  - token validated on app initialisation (format + expiry) before rendering protected routes;
  - token stored by `TokenManager` in sessionStorage (cleared on browser close), falling back to localStorage if unavailable;
    `login()` also writes `auth-token` / `user-profile` to localStorage.

## Real backend order API
- `GET /api/orders` → `{items: OrderRow[], totalCount, pageCount, currentPage}`; `OrderRow` = `{id, orderId ("#1A2B3C4D"), customerName, itemsOrdered ("2x Burger, Fries"),
  deliveryETA, status, estimatedWaitTime (min, null without ETA), itemCount, createdAt, updatedAt}`. `sortBy` is `created` (default), `eta`, `status` (alphabetical), `customerName`, `orderId`.
- `POST /api/orders` accepts `sessionId` (alias `customerSessionId`), optional `customerName`, `estimatedMinutes` (0–1440), `items[{name|menuItemId, quantity 1–99}]` (≤50);
  there is no menu yet, so `menuItemId` is stored as the item name. Response: `{id, orderId, sessionId, status, version, createdAt, updatedAt}`.
- `GET /api/orders/poll?since=&eTag=` → `{orders, eTag}` (≤200 newest changes) or 304.
- Status names are the backend's: `CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED` (the MSW mock still says `PREPARING` in places).
- SSE: `GET /api/orders/stream` pushes `order-status-updated` events `{id, orderId, status}` (browsers cannot set headers on `EventSource`, so this one endpoint also accepts `?token=<jwt>`; keep query strings out of access logs). Set `SSE_REDIS_ENABLED=true` to relay them through Redis pub/sub when running several replicas.

## Errors (RFC 7807)
```json
{ "type": "urn:readybell:{context}:{error}", "title": "…", "status": 400, "detail": "…" }
```
Parsed by the `ProblemDetail` interface/parser in `src/types/errors.ts`.
Status codes: 200, 201, 304 (poll unchanged), 400, 401, 403, 404, 409 (concurrent modification), 500.

## Mock backend (MSW)
- 100 generated orders per request: 20 % CREATED, 30 % IN_PROGRESS, 20 % READY, 20 % COMPLETED, 10 % CANCELLED.
- Error triggers: `/api/orders/error/{network|401|403|500}`.
- SSE ticks about every 3 s.
- Env: `BACKEND_URL` (Vite dev-proxy target, default `http://localhost:8080`); `VITE_API_BASE_URL` (client base URL, default same origin — only for deployments on a different origin). Backend-side: `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` (Web Push), `SESSION_TIMEOUT_MINUTES` (30).
- MSW is opt-in (`VITE_USE_MOCKS=true`); the default is the real backend through the dev proxy. See [DEVELOPMENT.md](DEVELOPMENT.md).

## Integration checklist
Orders list/filter/sort/status update; SSE + polling fallback; login; 401 for invalid token; 403 for cross-tenant; error messages rendered from `ProblemDetail`.

## Spec alignment
Frontend verified against the backend spec (13/13 alignment criteria met): tenant isolation, RFC 7807 errors,
SSE with polling fallback, Bearer auth. See [PROGRESS.md](PROGRESS.md).
