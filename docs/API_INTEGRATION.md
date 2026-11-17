# Dashboard ↔ Backend API

The dashboard talks to the Spring Boot API under `/api` (same origin in production, Vite dev proxy in
development). Errors are RFC 7807 `application/problem+json` (`type`, `title`, `status`, `detail`).
Client code: `dashboard-ui/src/utils/api.ts` (staff API, Bearer token) and `dashboard-ui/src/utils/trackApi.ts`
(public customer API, no token).

## Public (no credentials)

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/login` | `{restaurant, username, password}` → `{token, role, tenantId, username, expiresIn}`. `restaurant` is the restaurant's identifier: usernames are unique only **within** a restaurant, so two restaurants can both have an `admin`. Leave it out only for the platform operator. An unknown restaurant, username or password all give the same `401`; throttled after repeated failures (429, counted per restaurant + username) |
| POST | `/api/auth/refresh` | exchange a token (valid, or expired by less than `readybell.security.jwt.refresh-grace`, 24 h) for a new one of the same sign-in chain. The old token is retired after 60 s; presenting a retired token again ends the whole chain (it was copied) |
| POST | `/api/auth/logout` | `204`: ends the sign-in chain of the Bearer token, so that token and every token refreshed from it stop working at once. Harmless without or with an invalid token |
| POST | `/api/sessions` | `{qrToken}` (the restaurant identifier) → new customer session |
| GET | `/api/sessions/{id}/track` | tracking page data: restaurant name, `sessionActive`, orders with items and ETA; 404 for an unknown id |
| GET | `/api/sessions/{id}/orders` | `[{orderId, status}]` |
| GET | `/api/sessions/{id}/orders/stream` | SSE, event `order-status-updated` |
| POST | `/api/sessions/{id}/subscriptions` | `{endpointUrl, p256dhKey, authKey}` Web Push subscription. The server calls `endpointUrl`, so it must be `https`, on the default port, without credentials, and a host of a known push service (FCM, Mozilla, Apple, Windows WNS; more via `readybell.push.extra-allowed-hosts`); anything else is a 400. Lengths: endpoint ≤ 2048, `p256dhKey` ≤ 200, `authKey` ≤ 100. At most 5 devices per session (`429`, `too-many-subscriptions`); re-registering a known device only refreshes its keys |
| DELETE | `/api/sessions/{id}/subscriptions/{subscriptionId}` | revoke |
| GET | `/api/push/config` | `{enabled, publicKey}`: the VAPID public key the browser subscribes with |
| GET | `/actuator/health` | probes (`/liveness`, `/readiness`) |

The session id in the URL is the credential for the customer endpoints; the tenant is derived from it.

**QR handed over in person.** The only way to the customer's tracking page is the unguessable session id in the
link. Staff show the QR of the order (right after entering it, or later from the orders list), or the customer-facing
screen `/display` shows it. There is deliberately no public "receipt number to session" lookup: receipt numbers are
small and sequential, so such a lookup can be guessed, and whoever got there first would also receive the push.
The first open of `/track/{sessionId}` marks the order connected (`order.connected_at`); the staff stream then gets
`order-connected`, and `GET /api/orders` rows carry `sessionId` and `connected` (staff only). The QR is hidden on the
staff screens once an order is connected, so nobody behind in the queue can scan it.

**Sign-in and usernames.** A staff username is unique within its restaurant (`UNIQUE (tenant_id, username)`), stored lower-case,
and compared case-insensitively together with the restaurant identifier. The token carries the username (`sub`) and the
restaurant (`tenant_id`); refresh and the per-request "is the account still active" check look the account up by both. HTTP
Basic (curl, tests) takes `restaurant/username` as the user name, or the bare name for a configured user
(`readybell.security.users`): `curl -u aurora/admin:... https://host/api/restaurant`.

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

## Conventions

- Status names are the backend's: `CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED`; the staff UI labels `IN_PROGRESS` "Preparing".
- Roles: `STAFF`, `RESTAURANT_ADMIN`, `PLATFORM_ADMIN`.
- The UI refreshes the order list on SSE events and falls back to polling every 30 s while the stream is down.

## Rate limits (public endpoints)

Sliding windows, defaults in `application.yml` under `readybell.rate-limit.rules`. **Every rule that matches a request is
applied**, and a rule counts per `key-by`: `ip` (default), `session` (the id in `/api/sessions/{id}/...`) or `global`. Customers of
one restaurant share one public address, so what a customer page does is limited **per session** (track 30/min, poll 60/min,
stream 10/min, subscribe 10/min) and the per-address rule beside it is only a high ceiling (track 600/min, poll 1200/min,
stream 120/min, subscribe 60/min). Session start (`POST /api/sessions`, not used by the customer flow): 30/min per address and
300/min for everybody. Counter claim: only *failed* claims count, 10/min per address and 100/min per restaurant (successful
claims are free; 120 requests/min per address overall); restaurant name 60/min. Over the limit: `429` with
`Retry-After` (seconds) and `application/problem+json` (`type: urn:readybell:rate-limit:exceeded`). Paths are matched decoded
(`/api/%73essions` is `/api/sessions`). Behind a reverse
proxy set `RATE_LIMIT_TRUSTED_PROXIES` (IP/CIDR list) so `X-Forwarded-For` is used; without it the proxy's own address is
the key and all clients share one budget (see DEPLOYMENT.md). `RATE_LIMIT_REDIS_ENABLED=true` shares counters between replicas.
Metric: `ratelimit.rejected{endpoint}` at `/actuator/metrics`.

## Login throttling

Failed logins are counted per **account + address** (5 in 15 min, locks only that combination, so a stranger cannot lock the
real owner out from another address), per **address** (30, many accounts from one address) and per **account** (100, a
distributed guess; deliberately high because it locks the account for everyone). Over a limit: `429`, `Retry-After`,
`urn:readybell:auth:too-many-attempts`. Properties: `readybell.security.jwt.login.max-failures`,
`.max-failures-per-address`, `.max-failures-per-account`, `.window`. `READYBELL_SECURITY_PLATFORM_ADMIN_ALLOWED_NETWORKS`
(IPs/CIDRs) restricts where the platform operator can sign in or refresh a token; from elsewhere the answer is the same as for
a wrong password.

## Token lifecycle

Staff tokens are HS256 JWTs (1 h) with `jti` (the token) and `sid` (the sign-in chain shared by all refreshed tokens). Every request
checks that the chain and token are not revoked and, through a short cache (`readybell.security.account-check-cache`, 30 s),
that the account is still active: a deleted staff member or a deactivated restaurant loses access within that time, not when the
token expires. Revocations live in memory, or in Redis when `SSE_REDIS_ENABLED=true` so every replica honours them (during a Redis
outage they apply per node). Tokens issued before chains existed are their own chain.

