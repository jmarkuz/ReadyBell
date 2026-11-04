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
| GET | `/api/push/config` | `{enabled, publicKey}`: the VAPID public key the browser subscribes with |
| GET | `/actuator/health` | probes (`/liveness`, `/readiness`) |

The session id in the URL is the credential for the customer endpoints; the tenant is derived from it.

## Staff (Bearer token, tenant taken from the token)

| Method | Path | Role | Notes |
|--------|------|------|-------|
| GET | `/api/restaurant` | any | `{name, identifier}` of the user's restaurant |
| GET | `/api/orders` | any | `page`, `pageSize` (≤100), `status` (repeatable), `sortBy` (`created`, `eta`, `status`, `customerName`, `orderId`), `sortOrder`, `search` → `{items, totalCount, pageCount, currentPage}` |
| POST | `/api/orders` | any | `{sessionId?, customerName?, estimatedMinutes?, items?[{name, quantity}]}`; without `sessionId` the server starts a session (the receipt QR links to `/track/{sessionId}`) |
| PATCH | `/api/orders/{id}/status` | any | `{status}`; lifecycle `CREATED → IN_PROGRESS → READY → COMPLETED`, `CANCELLED` from any open state (422 otherwise, 409 on a concurrent change) |
| GET | `/api/orders/stream` | any | SSE; the token may be passed as `?token=` because `EventSource` cannot set headers |
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
