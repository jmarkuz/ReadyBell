# First MVP: gap analysis and execution plan

Source of truth for scope: [ReadyBell MVP Definition](product/Pingood_MVP_Definition.md) (the file keeps its original name).
Rule from the definition: every feature must answer "does this help replace physical notification devices?" Anything else is deferred.
Overall status of all improvement tasks: [IMPROVEMENTS.md](IMPROVEMENTS.md).

## MVP must-haves vs. the code

| MVP requirement | State |
|-----------------|-------|
| QR session, anonymous customer session | Done (receipt QR → `/track/{sessionId}`) |
| Order status view with wait time | Done |
| Browser push subscription, delivery, delivery tracking | Done in code; outbox guarantees the event; **not verified on real devices** (ops, P1) |
| Restaurant onboarding (platform admin), staff accounts | Done |
| Staff dashboard: create order, change status, mark ready | Done (server-side paging, metrics, SSE) |
| Multi-tenant, authentication, tenant isolation | Done (structural tenant filter, guard tests) |
| **QR at the pickup area** ("QR display at pickup area") | **Missing** → M2 (needs readable order numbers → M1) |
| **Order identifier** the customer can read out / type | **Weak**: UUID prefix `#3F9A21C0` → M1 |
| **Basic monitoring** and measurable success criteria (500+ sessions, push < 2 s) | **Partial**: Prometheus endpoint exists, but no business metrics or alert rules → M3 |
| Reliability of data in a pilot | **Missing**: no backup / restore procedure → M4 |
| Pilot readiness: deploy, real-device push check, onboarding < 5 min | **Missing**: no runbook → M5 (the execution itself is manual) |

## Deferred (not needed to validate the hypothesis)

T13 staff sound alert, staff-panel i18n (Q2), remaining accessibility gaps (Q3), customer-page extras (Q4), moving the
staff token out of `localStorage` (S3), single-node housekeeping lock (R3), architecture follow-ups A2/A3, `api.ts`
split (U1), `.kiro` clean-up (Q5), restaurant "location" setting. Out-of-scope list of the MVP definition (loyalty,
promotions, reviews, POS, native apps, analytics) is untouched.

## Tasks

| ID | Task | Size | Depends on | Branch | Notes |
|----|------|------|-----------|--------|-------|
| M1 | Sequential order numbers per restaurant (T11, F1) | M | – | `feat/sequential-order-numbers` | Flyway migration with backfill; number in API, receipt, tracking, push text, search/sort |
| M2 | Counter QR flow (T12, P4) | M | M1 | `feat/counter-qr-flow` | Public claim endpoint, PWA form, printable restaurant QR; reuses rate limits (T08) |
| M3 | Business metrics and alert rules (R2) | M | – | `feat/business-metrics-alerts` | Sessions started, orders by status, push sent/failed, ready→push latency, SSE connections; Prometheus alert rules; `docs/MONITORING.md` |
| M4 | PostgreSQL backup and restore drill (P3) | S | – | `ops/postgres-backup` | `scripts/backup.sh`, `scripts/restore.sh`, drill script run in CI-free local check, section in `DEPLOYMENT.md` |
| M5 | Pilot checklist (P1, P2 as documents) | S | – | `docs/pilot-checklist` | Real-device push test, deployment steps, restaurant onboarding < 5 min timing sheet |

## Parallelism

```
Wave A (parallel, disjoint files):  M1   M3   M4   M5
Wave B (after M1 is merged):        M2
```

M1, M3, M4 and M5 touch different areas (ordering numbering; metrics config and docs; scripts and deployment docs;
a new pilot document). Agents do not edit `IMPROVEMENTS.md`; the tracker is updated once per merge by the coordinator.
M2 starts only after M1 is on `master` because it relies on per-restaurant order numbers.

## Definition of done for every task

- Specification criteria satisfied (M1/M2: [T11](tasks/T11-sequential-order-numbers.md), [T12](tasks/T12-counter-qr-flow.md)).
- `./gradlew check` and, for UI changes, `npm test`, `npm run lint`, `npx tsc --noEmit` pass locally.
- Pull request against `master` with CI green (backend, frontend, e2e, image) before merge.
- Tracker row and log line updated in `IMPROVEMENTS.md`.

## Progress

| Task | Status | PR |
|------|--------|----|
| M1 | Todo | – |
| M2 | Todo | – |
| M3 | Todo | – |
| M4 | Todo | – |
| M5 | Todo | – |
