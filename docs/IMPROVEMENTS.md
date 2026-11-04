# Improvement plan and progress tracker

Next improvements after the architecture cleanup and the Incoming Orders pagination (PR #8). Each task has a full
specification in [`tasks/`](tasks/). This file is the single place to track status: update the **Status** column and
the **Log** at the bottom when work starts or finishes.

Relation to [BACKLOG.md](BACKLOG.md): the backlog stays the long list of everything known. This plan is the ordered
subset to do next, plus findings from the latest analysis (T01–T04). "Backlog" column shows the matching backlog ID.

**Status values:** `Todo` · `In progress` · `In review` · `Done` · `Blocked` · `Dropped`
**Sizes:** S ≤ ½ day, M ≈ 1–2 days, L > 2 days.

## Summary of the analysis

- **Incoming Orders only sees the 100 newest orders.** `useDashboardOrders` loads one page of 100; search, status and date
  filters, sorting and the new pagination all run on the client. The backend already supports paging, status, search and
  sorting. Metric cards are also computed from those 100, so they are wrong beyond that (T01, T02).
- **Table details:** the sort toggle calls `setSortOrder` inside another state updater (suspected double toggle under
  `StrictMode`), the page is not reset on sort/filter change, `SortIndicator` is re-created on every render (T03).
- **No index for the list query** `(tenant_id, status, created_at)` (T04).
- **Reliability and security** items from the review remain the biggest product risks: no outbox for `OrderReady` (T05),
  tenant filter enabled by hand in 17 methods (T06), staff JWT in the SSE URL (T07), public endpoints without rate limits (T08).
- **Quality:** application services have no unit tests (T09), no browser E2E (T10).
- **Product:** UUID-prefix order numbers (T11), no counter QR flow (T12), no new-order alert for staff (T13).

## Wave 1: Incoming Orders correctness (do first)

| ID | Task | Size | Status | Depends on | Backlog | Spec |
|----|------|------|--------|-----------|---------|------|
  | T04 | Indexes for the order list query | S | Done | – | – | [T04](tasks/T04-order-list-indexes.md) |
| T03 | Orders table fixes (sort toggle, page reset, a11y of headers) | S | In review | – | Q3 (part) | [T03](tasks/T03-orders-table-fixes.md) |
| T01 | Server-side pagination, filters and sorting | M | Done | PR #8, T04 | – | [T01](tasks/T01-server-side-orders-paging.md) |
| T02 | Dashboard metrics from the server | S | In review | – | A1 (part) | [T02](tasks/T02-server-side-metrics.md) |

## Wave 2: reliability and security (before the first pilot)

| ID | Task | Size | Status | Depends on | Backlog | Spec |
|----|------|------|--------|-----------|---------|------|
| T05 | Transactional outbox for `OrderReady` | L | Todo | – | R1 | [T05](tasks/T05-transactional-outbox.md) |
| T06 | Structural tenant filter + guard test | M | In review | – | S1 | [T06](tasks/T06-structural-tenant-filter.md) |
| T07 | Single-use ticket for the SSE stream | S | In review | – | S2 | [T07](tasks/T07-sse-ticket.md) |
| T08 | Rate-limit public endpoints | S | In review | – | S4 | [T08](tasks/T08-public-rate-limits.md) |

## Wave 3: quality and product

| ID | Task | Size | Status | Depends on | Backlog | Spec |
|----|------|------|--------|-----------|---------|------|
| T09 | Unit tests for application services, raise coverage floor | M | Todo | – | – | [T09](tasks/T09-application-tests-coverage.md) |
| T10 | Playwright E2E | M | In review | – | Q1 | [T10](tasks/T10-playwright-e2e.md) |
| T11 | Sequential order numbers per restaurant | M | Todo | – | F1 | [T11](tasks/T11-sequential-order-numbers.md) |
| T12 | Counter QR flow (customer enters the order number) | M | Todo | T08, T11 | P4 | [T12](tasks/T12-counter-qr-flow.md) |
| T13 | Sound / vibration alert for new orders | S | Todo | – | Q6 (part) | [T13](tasks/T13-new-order-alert.md) |

## Not in this plan (still in the backlog)

Operations before the pilot (P1 push on real devices, P2 deployment, P3 PostgreSQL backups) need a real host and
devices, so they are not coding tasks; keep following [BACKLOG.md](BACKLOG.md). Also left there: R2 alerting,
R3 single-node housekeeping, S3 token storage, A1–A3 architecture follow-ups, U1/U2 frontend refactors, Q2 i18n,
Q3 remaining accessibility gaps, Q4 customer page extras, Q5 `.kiro` specs.

## Progress

| Wave | Done / total |
|------|--------------|
| 1 | 3 / 4 (T02 in review) |
| 2 | 0 / 4 (T06, T07, T08 in review) |
| 3 | 0 / 5 |

## Log

| Date | Change |
|------|--------|
| 2026-11-04 | Plan created from the project analysis; 13 task specifications added in `docs/tasks/`. |
| 2026-11-04 | T04: added `V2__order_list_indexes.sql` and `scripts/explain-order-list.sh`; on 100k orders the list query uses the new index (0.26 ms). |
| 2026-11-04 | T04 done: Flyway applies V2 on a fresh and on a pre-Flyway Postgres 16 database, `ddl-auto: validate` passes. T03 started. |
| 2026-11-04 | T01: `GET /api/orders` gets `from`/`to` (created_at, 400 if from > to); `useDashboardOrders(query)` loads one page; table in server mode (no client sort/slice, Items not sortable); page resets via a filter key (one request per change). Merged in PR #10. |
| 2026-11-04 | T02: `GET /api/orders/metrics` (one grouped query, tenant-isolated), `getOrderMetrics` client, `useDashboardOrders` exposes `metrics` and refreshes them with the list; client-side count is only a fallback until first load. Backend and 716 UI tests pass. |
| 2026-11-04 | T02 rebased onto the merged T01: `useDashboardOrders` returns `metrics` from `GET /api/orders/metrics` instead of the 100-newest `recentOrders`; `DASHBOARD_ORDER_LIMIT` removed. 717 UI tests pass. |
| 2026-11-04 | T07: `POST /api/orders/stream/ticket` issues a 30 s single-use ticket (maps to the caller's JWT, so user and tenant are preserved); `GET /api/orders/stream` accepts only `?ticket=`; `?token=` removed. `useSSE` takes `getTicket` and fetches a fresh ticket on every (re)connect, retrying with backoff if that fails. Backend suite and 718 UI tests pass. Access-log masking not done: tickets are harmless after one use, and no access log is configured in the repo. |
| 2026-11-04 | T08: `RateLimitFilter` (before Spring Security) applies per-IP sliding-window limits from `readybell.rate-limit.rules` to session start, push subscribe, track, customer poll and customer stream; 429 + `Retry-After` as problem+json, metric `ratelimit.rejected{endpoint}`. Client IP honors `X-Forwarded-For` only from `trusted-proxies` (right-most untrusted hop). Redis counters with per-node fallback. Disabled in the test profile; `RateLimitApiTest` enables it. |
| 2026-11-04 | T06: `TenantFilterInterceptor` (Spring advisor, inside the repository transaction) binds `tenantFilter` for every `@Repository` in `adapter.out.persistence`: tenant from the `TenantId` argument, else the request context, else `MissingTenantContextException`; `@CrossTenant("reason")` methods run with the filter off. All 17 manual `enableTenantFilter` calls removed. `TenantGuardArchitectureTest` fails for an adapter method with neither a `TenantId` nor `@CrossTenant`; `TenantFilterBindingTest` checks two tenants and the session's filter state. `NotificationRepository.findById` became `findByTenantAndId(TenantId, ...)`. Background threads need no context: every call passes a tenant or is `@CrossTenant`. |
| 2026-11-04 | T10: Playwright E2E in `dashboard-ui/e2e/` (7 tests: sign-in x3, order -> QR -> live customer tracking to READY, unknown link, server-side paging with 25 generated orders, dark theme); `webServer` starts the dev-profile backend (port 18080, DB `readybell_e2e`) and Vite (5273); new `e2e` CI job uploads report, traces and videos on failure; `docs/DEVELOPMENT.md` documents it. **Found and fixed a T07 regression:** `useDashboardOrders` passed `getTicket` as an inline arrow, so `useSSE` reopened the stream on every render (ticket -> stream -> abort loop) and starved the browser's connections until "Create order" hung; `getTicket` is now read through a ref and a unit test covers it. 10 consecutive green local runs (35-55 s each); the CI job has not run yet, so the <5 min and <1 % flakiness criteria are still to be confirmed there. |
