# Architecture review

Status of the project against its own architecture documents (`docs/architecture/`) and the decisions taken along
the way. Written after the cleanup that this review triggered; the open items are tracked in [BACKLOG.md](BACKLOG.md).

## Verdict

The structure is sound: a modular monolith with hexagonal bounded contexts, a framework-free domain, event-driven
integration, strict tenant isolation. The main drift was *around* the edges (a dependency cycle between two
contexts, read-side endpoints that skipped the application layer, dead code and stale docs). Those are fixed and,
where possible, now enforced by tests. No behaviour changed.

## What matches the architecture

| Rule | Evidence |
|------|----------|
| Domain and application layers are plain Java | `ArchitectureTest` (no Spring/JPA/servlet in `*.domain` / `*.application`, except the JPA base class in `shared.domain.jpa`) |
| Dependencies point inward | `ArchitectureTest`: no `domain`/`application` → `adapter`; inbound adapters never use outbound ones |
| Contexts do not form cycles | `ArchitectureTest`: `session`, `ordering`, `subscription`, `restaurant`, `notification`, `platformadmin` are cycle-free; `shared` knows no context; no context knows `platform` |
| Tenant isolation | Hibernate `tenantFilter` on tenant-scoped entities + `TenantResolutionFilter`; `TenantIsolationApiTest` |
| Integration through events | `OrderReady` → async `OrderReadyHandler`; `OrderStatusUpdated` → SSE fan-out; `DomainEventPublisher` port |
| Swappable infrastructure | `NotificationSender` port (VAPID adapter), `DomainEventPublisher`, repositories as ports |
| One deployable | Dockerfile bundles the UI into the API; Flyway owns the schema |

## Decisions taken along the way (and whether they hold)

| Decision | Why | Verdict |
|----------|-----|---------|
| **Receipt QR links to a session the server creates with the order** (`/track/{sessionId}`), instead of a per-restaurant QR | Staff can enter an order before any customer scan; no change to the order aggregate (it keeps a mandatory session) | Holds. Trade-off: the counter QR flow (customer types an order number) is not built |
| Unguessable session UUID is the customer's credential | No accounts (MVP rule); tenant derived from the session | Holds; sessions are ended after 24 h without open orders, and their push subscriptions deleted |
| Push on its own executor with bounded retries | Marking an order READY must not wait on push services | Holds. Gap: no outbox, so a crash between "saved" and "published" loses the notification (BACKLOG R1) |
| Flyway baseline V1 + `ddl-auto: validate` (dev keeps `create`) | Schema changes need a history; existing DBs are adopted at V1 | Holds; verified on a fresh and an existing PostgreSQL |
| `OrderStatus.IN_PROGRESS` in the API, "Preparing" in the UI | Keep the backend vocabulary, translate at the edge | Holds |
| Login throttle shared through Redis, local fallback | Several replicas must apply one limit; Redis outage must not disable it | Holds |
| Dev-only test-data endpoints behind the `dev` profile | Fast UI testing without a customer flow | Holds; see "Accepted debt" |
| Customer page uses plain `fetch` (no staff token) | A leftover staff token on a shared phone must not be sent | Holds |

## Fixed in this cleanup

- **Cycle `session` ↔ `ordering`**: the customer's order views lived in the session context but read orders. They moved to
  `ordering` (`CustomerOrdersController`, `TrackSessionUseCase`); `SessionController` now only starts sessions.
- **Controllers bypassing the application layer** (`/api/restaurant`, staff list, restaurant list): now `getRestaurant`,
  `listStaffAccounts`, `listRestaurants` on the use cases.
- **Duplicate `RestaurantNotFoundException`** (two classes, two messages): one remains, handled globally.
- Shared kernel and `subscription` imported adapter/other-context classes only for Javadoc: removed.
- Fully-qualified class names scattered through new code replaced by imports.
- Unused: H2 on the production runtime classpath; 8 unused client methods and their types; the MSW mock layer
  (browser worker, handlers, `VITE_USE_MOCKS`, service-worker file) that no longer matched the API; Vite template
  leftovers (`App.css`, `react.svg`, `vite.svg`); two long stale component READMEs; `rollup-plugin-visualizer`, `msw`.
- Tracked IDE files (`.idea/`) and the generated `.jqwik-database` are untracked and ignored.
- Docs rewritten where they described mocks or a different API (`API_INTEGRATION.md`, `DEVELOPMENT.md`).

## Accepted debt (known, deliberately left)

1. **Read side of ordering** (`OrderController` list/poll, `OrderPollingController`) uses `OrderRepository` directly. It is a
   thin query path; a `OrderQueryUseCase` would be cleaner (BACKLOG A1).
2. **`DevTestDataController`** (profile `dev`) reaches into other contexts' JPA repositories. Acceptable for a dev tool, but it
   is the only place that breaks the port rule (BACKLOG A3).
3. **Exception mapping is split**: 22 handlers in `GlobalExceptionHandler`, 2 local ones in `PlatformAdminController` (BACKLOG A2).
4. **`enableTenantFilter(...)` is called by hand in 17 repository methods.** Forgetting one leaks across tenants; isolation tests
   cover the main paths, not every method (BACKLOG S1).
5. **Order numbers** are the first 8 hex characters of the UUID: readable, not guaranteed unique, not sequential (BACKLOG F1).
6. **`api.ts` is a ~970-line module** (HTTP core, interceptors, token refresh, endpoints). It works and is tested but is the
   largest unit left to split (BACKLOG U1).

## Risks worth watching

- Staff JWT is kept in browser storage (XSS exposure) and the SSE stream takes it as `?token=` (shows up in proxy logs). CSP limits
  the first; the second needs a short-lived ticket (BACKLOG S2).
- Web Push is untested on real devices; iOS only works for a Home-Screen PWA over HTTPS (BACKLOG P1).
- Single-node assumptions: SSE registry is in memory (Redis relay exists), housekeeping runs on every replica (idempotent, but redundant).
