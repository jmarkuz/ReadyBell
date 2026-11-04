# Backlog

Ordered by priority. Sizes: S ≤ ½ day, M ≈ 1–2 days, L > 2 days. IDs are referenced from [REVIEW.md](REVIEW.md).
Done items are kept at the bottom for context.

## P0: before the first pilot restaurant

| ID | Task | Size | Notes |
|----|------|------|-------|
| P1 | Verify Web Push on real devices (Android Chrome, iOS Safari as Home-Screen PWA) with real VAPID keys over HTTPS | M | Cannot be automated here; checklist: subscribe, ready ping within 2 s, tap opens `/track/…`, expired endpoint is removed |
| P2 | Deploy to a real host with TLS, set a stable VAPID pair, create the platform operator with a bcrypt hash | S | `docs/DEPLOYMENT.md`; run `scripts/smoke.sh` against it |
| P3 | Backups + restore drill for PostgreSQL | S | Nothing exists today |
| P4 | Counter QR flow: one QR per restaurant, customer enters the order number from the receipt | M | Needs a public `POST /api/sessions/{id}/claim` (or similar) and a small form on the PWA |

## P1: reliability and security

| ID | Task | Size | Notes |
|----|------|------|-------|
| R1 | Transactional outbox for `OrderReady` (and status events) | L | Today: save, then publish in-process; a crash in between loses the notification |
| S1 | Make tenant filtering structural: enable the filter once per request (or Hibernate `@TenantId`) instead of per repository method | M | Add a test that fails when a repository query ignores the tenant |
| S2 | Replace `/api/orders/stream?token=` with a short-lived single-use ticket | S | Avoids long-lived tokens in logs |
| S3 | Move the staff token out of `localStorage` (httpOnly cookie + CSRF) or shorten TTL/refresh | M | Decide with the threat model |
| S4 | Rate-limit public endpoints (`POST /api/sessions`, subscriptions) per IP | S | Redis-backed, like the login throttle |
| R2 | Alerting on the Prometheus metrics (push failures, 5xx, SSE connections) and custom counters (sessions started, pushes sent/failed) | M | Metrics endpoint exists; no custom meters yet |
| R3 | Run housekeeping on one node only (ShedLock or a Redis lock) | S | Now idempotent but runs everywhere |

## P2: architecture follow-ups (from the review)

| ID | Task | Size |
|----|------|------|
| A1 | `OrderQueryUseCase` for list/poll so no controller touches `OrderRepository` | S |
| A2 | One place for exception mapping: move the two `PlatformAdminController` handlers into `GlobalExceptionHandler` (check response shapes first) | S |
| A3 | Give the dev test-data tool a port (or move its deletes into the owning contexts) | S |
| F1 | Per-restaurant sequential order numbers (`#101`) instead of UUID prefixes | M |
| U1 | Split `api.ts` into HTTP core + endpoint modules | M |
| U2 | Fix the 7 react-refresh warnings by separating context objects/hooks from providers (as done for the restaurant context) | S |

## P3: product and quality

| ID | Task | Size |
|----|------|------|
| Q1 | Browser E2E (Playwright): login, New order, QR link, customer sees READY | M |
| Q2 | i18n for the staff panel (uk/en); the customer page already has both | M |
| Q3 | Accessibility gaps from `UI_QUALITY.md` (aria-sort, skip link, aria-current, live regions) and four colour pairs below 3:1 | M |
| Q4 | Customer page: show "order cancelled" push, order history of a session, add-to-home-screen prompt | M |
| Q5 | Decide the fate of `.kiro/specs/*` (historical specs): archive or delete the three UI/integration ones that no longer describe the code | S |
| Q6 | Staff UX: sound/vibration on new order, status change from the QR dialog, edit/cancel order | M |

## Done in the cleanup that produced this list

- Cycle `session` ↔ `ordering` removed; ArchUnit rules added (`ArchitectureTest`).
- Application-layer queries for restaurant profile, staff list and restaurant list.
- Duplicate `RestaurantNotFoundException` merged; Javadoc-only cross-imports removed; FQNs tidied.
- Dead code removed: unused client methods, MSW mock layer, Vite leftovers, H2 on the runtime classpath, stale READMEs.
- IDE and generated files untracked; API and development docs rewritten.
