# Dashboard ↔ Backend API

The dashboard talks to the Spring Boot API under `/api` (same origin in production, Vite dev proxy in
development). Errors are RFC 7807 `application/problem+json` (`type`, `title`, `status`, `detail`).
Client code: `dashboard-ui/src/utils/api.ts` (staff API, Bearer token) and `dashboard-ui/src/utils/trackApi.ts`
(public customer API, no token).

## Public (no credentials)

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/login` | `{username, password}` → `{token, role, tenantId, username, expiresIn}`; throttled after repeated failures (429) |
| POST | `/api/auth/refresh` | refresh an expired token within the grace period |
| POST | `/api/sessions` | `{qrToken}` (the restaurant identifier) → new customer session |
| GET | `/api/sessions/{id}/track` | tracking page data: restaurant name, `sessionActive`, orders with items and ETA; 404 for an unknown id |
| GET | `/api/sessions/{id}/orders` | `[{orderId, status}]` |
| GET | `/api/sessions/{id}/orders/stream` | SSE, event `order-status-updated` |
| POST | `/api/sessions/{id}/subscriptions` | `{endpointUrl, p256dhKey, authKey}` Web Push subscription |
| DELETE | `/api/sessions/{id}/subscriptions/{subscriptionId}` | revoke |
| GET | `/api/restaurants/{identifier}/public` | counter QR page: `{name}` of an active restaurant; 404 otherwise |
| POST | `/api/restaurants/{identifier}/claim` | counter QR: `{orderNumber}` (the number on the receipt) → `{sessionId}` for `/track/{sessionId}`. Every failure (unknown restaurant, unknown number, already claimed, finished or older than `readybell.claim.max-age`, default 4 h) is the same generic `404`; each order can be claimed once; 10 tries/min per IP |
| GET | `/api/push/config` | `{enabled, publicKey}`: the VAPID public key the browser subscribes with |
| GET | `/actuator/health` | probes (`/liveness`, `/readiness`) |

The session id in the URL is the credential for the customer endpoints; the tenant is derived from it.

**Counter QR.** The restaurant prints one QR (Settings page of the restaurant admin) that opens `/r/{identifier}` in the
customer app. The customer types the number from the receipt; `POST /api/restaurants/{identifier}/claim` returns the
session of that order and the app continues on `/track/{sessionId}`. Receipt numbers are small and sequential, so the
endpoint is throttled per IP, hands an order out only once and only for `CLAIM_MAX_AGE` (default `4h`) after the order
was created, and never says why a claim failed.

## Order numbers

Every order has a per-restaurant sequential number (1, 2, 3 ...; unique within the restaurant, never reset, allocated in
the same transaction as the order so there are no duplicates or gaps). It is shown as `#101` everywhere a person sees an
order: `orderId` in the staff responses (`POST /api/orders`, status updates, `GET /api/orders` rows), `orderNumber` in
`GET /api/sessions/{id}/track`, the receipt/QR dialog and the order-ready push text. The order UUID (`id`) remains the
identifier for URLs and API calls. `GET /api/orders?sortBy=orderId` sorts numerically; `search` matches the digits of
the number (with or without `#`, prefix match: `10` finds #10 and #100-#109).

## Staff (Bearer token, tenant taken from the token)

| Method | Path | Role | Notes |
|--------|------|------|-------|
| GET | `/api/restaurant` | any | `{name, identifier}` of the user's restaurant |
| GET | `/api/orders` | any | `page`, `pageSize` (≤100), `status` (repeatable), `sortBy` (`created`, `eta`, `status`, `customerName`, `orderId`), `sortOrder`, `search` → `{items, totalCount, pageCount, currentPage}` |
| GET | `/api/orders/metrics` | any | → `{totalOrders, created, inProgress, ready, completed, cancelled}`: real per-status totals for the tenant, independent of list filters and paging |
| POST | `/api/orders` | any | `{sessionId?, customerName?, estimatedMinutes?, items?[{name, quantity}]}`; without `sessionId` the server starts a session (the receipt QR links to `/track/{sessionId}`) |
| PATCH | `/api/orders/{id}/status` | any | `{status}`; lifecycle `CREATED → IN_PROGRESS → READY → COMPLETED`, `CANCELLED` from any open state (422 otherwise, 409 on a concurrent change) |
| POST | `/api/orders/stream/ticket` | any | → `{ticket, expiresIn: 30}`: single-use ticket bound to the caller (Redis `SET EX` / `GETDEL` when `SSE_REDIS_ENABLED`, else in memory) |
| GET | `/api/orders/stream` | ticket | SSE; `EventSource` cannot set headers, so pass `?ticket=` from the call above (one use, 30 s). `?token=` is no longer accepted; no ticket → 401. Events: `order-status-updated` `{id, orderId, status}` and, for the staff stream only, `order-created` `{id, orderId, status: "CREATED", event: "order-created", orderNumber: "#12"}` (sent after the order's transaction commits; the dashboard reloads and alerts staff) |
| GET | `/api/orders/poll` | any | `since` / `eTag`; 200 `{orders, eTag}` or 304 |
| GET / POST | `/api/admin/staff` | RESTAURANT_ADMIN | list / create `{username, password (8–72), role}` |
| DELETE | `/api/admin/staff/{accountId}` | RESTAURANT_ADMIN | deactivate |
| PUT | `/api/admin/config` | RESTAURANT_ADMIN | `{name}` |

## Platform operator (PLATFORM_ADMIN, cross-tenant)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/platform/restaurants` | list |
| POST | `/api/platform/restaurants` | `{identifier, name, adminUsername?, adminPassword?}` (admin fields together) |
| PUT | `/api/platform/restaurants/{tenantId}` | `{name}` |
| DELETE | `/api/platform/restaurants/{tenantId}` | deactivate (new customer sessions are refused) |
| GET | `/actuator/prometheus` | metrics |

## Development only (`dev` profile)

`POST /api/dev/test-data?count=n` (generate), `POST /api/dev/test-data/import` (`{orders:[…]}`), `DELETE /api/dev/test-data`
(removes the orders whose customer name ends with ` (test)`).

## Conventions

- Status names are the backend's: `CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED`; the staff UI labels `IN_PROGRESS` "Preparing".
- Roles: `STAFF`, `RESTAURANT_ADMIN`, `PLATFORM_ADMIN`.
- The UI refreshes the order list on SSE events and falls back to polling every 30 s while the stream is down.

## Rate limits (public endpoints)

Per client IP, sliding window (defaults in `application.yml` under `readybell.rate-limit.rules`): session start 20/min,
counter claim 10/min (restaurant name 60/min), push subscribe 20/min, track 120/min, customer poll 240/min, customer stream 30/min. Over the limit: `429` with
`Retry-After` (seconds) and `application/problem+json` (`type: urn:readybell:rate-limit:exceeded`). Behind a reverse
proxy set `RATE_LIMIT_TRUSTED_PROXIES` (IP/CIDR list) so `X-Forwarded-For` is used; without it the proxy's own address is
the key and all clients share one budget. `RATE_LIMIT_REDIS_ENABLED=true` shares counters between replicas.
Metric: `ratelimit.rejected{endpoint}` at `/actuator/metrics`.
