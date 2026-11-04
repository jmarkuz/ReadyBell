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
| T01 | Server-side pagination, filters and sorting | M | In review | PR #8, T04 | – | [T01](tasks/T01-server-side-orders-paging.md) |
| T02 | Dashboard metrics from the server | S | Todo | – | A1 (part) | [T02](tasks/T02-server-side-metrics.md) |

## Wave 2: reliability and security (before the first pilot)

| ID | Task | Size | Status | Depends on | Backlog | Spec |
|----|------|------|--------|-----------|---------|------|
| T05 | Transactional outbox for `OrderReady` | L | Todo | – | R1 | [T05](tasks/T05-transactional-outbox.md) |
| T06 | Structural tenant filter + guard test | M | Todo | – | S1 | [T06](tasks/T06-structural-tenant-filter.md) |
| T07 | Single-use ticket for the SSE stream | S | Todo | – | S2 | [T07](tasks/T07-sse-ticket.md) |
| T08 | Rate-limit public endpoints | S | Todo | – | S4 | [T08](tasks/T08-public-rate-limits.md) |

## Wave 3: quality and product

| ID | Task | Size | Status | Depends on | Backlog | Spec |
|----|------|------|--------|-----------|---------|------|
| T09 | Unit tests for application services, raise coverage floor | M | Todo | – | – | [T09](tasks/T09-application-tests-coverage.md) |
| T10 | Playwright E2E | M | Todo | – | Q1 | [T10](tasks/T10-playwright-e2e.md) |
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
| 1 | 1 / 4 |
| 2 | 0 / 4 |
| 3 | 0 / 5 |

## Log

| Date | Change |
|------|--------|
| 2026-11-04 | Plan created from the project analysis; 13 task specifications added in `docs/tasks/`. |
| 2026-11-04 | T04: added `V2__order_list_indexes.sql` and `scripts/explain-order-list.sh`; on 100k orders the list query uses the new index (0.26 ms). |
| 2026-11-04 | T04 done: Flyway applies V2 on a fresh and on a pre-Flyway Postgres 16 database, `ddl-auto: validate` passes. T03 started. |
| 2026-11-04 | T01: `GET /api/orders` gets `from`/`to` (created_at, 400 if from > to); `useDashboardOrders(query)` loads one page; table in server mode (no client sort/slice, Items not sortable); page resets via a filter key (one request per change). The metric cards still use the 100 newest orders (separate request) until T02. |
