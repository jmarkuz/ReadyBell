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
- Still **not** in the real backend: paginated/sortable/searchable `GET /api/orders`, `GET /api/orders/poll` for staff, and the richer `OrderRow`
  fields (customer name, items, ETA) — the backend returns `{orderId, sessionId, status, version}` lists. Only the mock provides those today.
- `Authorization: Bearer <token>`; tenant is derived from the token and enforced at the query layer
  (cross-tenant access → 403).
- Mock credentials: `staff@restaurant.com / password` (STAFF), `admin@restaurant.com / password` (ADMIN).
- Client behaviour (all tested):
  - request interceptor injects the Bearer header;
  - response interceptor handles 401 with a token refresh and request retry;
  - refresh uses up to 3 attempts with exponential backoff (1 s, 2 s, 4 s) and a 10 s timeout per attempt;
    400/401/403 are permanent failures (no retry), 5xx / network / timeout are retried;
  - token validated on app initialisation (format + expiry) before rendering protected routes;
  - token stored by `TokenManager` in sessionStorage (cleared on browser close), falling back to localStorage if unavailable;
    `login()` also writes `auth-token` / `user-profile` to localStorage.

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
- Env: `VITE_API_BASE_URL` (default `http://localhost:8080`, also used by the Vite dev proxy). Backend-side: `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` (Web Push), `SESSION_TIMEOUT_MINUTES` (30).
- In dev, `src/main.tsx` always starts MSW (`onUnhandledRequest: 'bypass'`) and seeds a mock STAFF token if none is stored.
  To hit the real backend, remove/guard the `worker.start()` block there. (There is no `VITE_USE_REAL_BACKEND` switch in the code — older docs claimed one.)

## Integration checklist
Orders list/filter/sort/status update; SSE + polling fallback; login; 401 for invalid token; 403 for cross-tenant; error messages rendered from `ProblemDetail`.

## Spec alignment
Frontend verified against the backend spec (13/13 alignment criteria met): tenant isolation, RFC 7807 errors,
SSE with polling fallback, Bearer auth. See [PROGRESS.md](PROGRESS.md).
