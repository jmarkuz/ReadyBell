# Progress & Results Log

Condensed record of completed dashboard-ui tasks (task numbers match `.kiro/specs/*/tasks.md`).
Detailed per-task reports were removed; git history keeps them.

## Dashboard UI (theme switcher spec)

| Task | Result |
|------|--------|
| 1.1 Project setup | Vite 5 + React + TypeScript (strict) + Tailwind + Vitest + ESLint; dev server on :5173 |
| 1.2–1.7 / 2.x / 3.x / 4–5 Foundations | Types, API client + mocking, test infra; Layout (280 px sidebar desktop, 80 px tablet, drawer mobile), Header (300 ms debounced search, notification badge, theme toggle, settings), Sidebar (7 items, profile, collapse), React Router v6 (7 routes), Dashboard page (summary cards, TanStack Table v8 orders table with sort/pagination, skeleton, status badges), ThemeToggle |
| 6.1 / 6.3 / 6.4 Search & filters | Debounced header search, `DateRangeFilter` (presets + custom), filters integrated with the table (76 + 27 + dedicated integration tests) |
| 7.2 / 7.4 Real-time | `useOrderUpdates` / `useOrderPolling` hooks (since removed: the dashboard now reloads from the API on SSE events via `useDashboardOrders`) |
| 8.1 Semantic HTML & ARIA | `header[role=banner]`, `nav`, `main#main-content`, `role=search`, labelled controls, `aria-expanded/haspopup`, live regions |
| 8.2 Keyboard navigation | 32 unit + 39 integration tests; integration suite initially 28/39 (missing Router context), fixed to 39/39 |
| 8.3 Screen reader | 7 of 10 criteria met; **open gaps** (aria-sort, skip link, aria-current, live regions, headings) listed in [UI_QUALITY.md](UI_QUALITY.md) |
| 8.4 Colour contrast (WCAG AA) | Palette verified; primary text 21:1 / 12.5:1. A few pairs only meet 3:1 (secondary text on light surface 3.9, dark COMPLETED badge 3.5) — full tables in [UI_QUALITY.md](UI_QUALITY.md) |
| 8.5 Responsive | Verified at 320 / 768 / 1024 px (emulation): no horizontal scroll except table, readable fonts, 44 px touch targets |
| 8.6 Mobile theme persistence | `localStorage` key `readybell-theme` works on iOS Safari / Android Chrome (emulated); in Safari Private Mode it is cleared when the session ends |
| 9.1 Code splitting | `React.lazy` + `Suspense` per page, manual vendor chunks; main bundle 57.75 KB gzipped (target 150 KB); details in [UI_QUALITY.md](UI_QUALITY.md) |
| 9.2 Memoization | `React.memo` on 6 components, `useMemo` for 5 computations, `useCallback` for Header/table/Dashboard handlers |

## Backend integration (backend-endpoints-integration spec)

| Task | Result |
|------|--------|
| 1.1.1–1.1.4 Bearer auth | Token manager, request interceptor, 401 response interceptor with refresh, retry with exponential backoff |
| 1.1.6 Token validation on init | Format/expiry check before protected routes |
| 1.1.7 Bearer flow E2E | Store-after-login, header injection, refresh and logout flows verified |
| 1.2.1–1.7 Errors & endpoints | RFC 7807 `ProblemDetail` parser (51 tests); all 22 endpoints in the client with MSW mocks |
| Alignment | Frontend ↔ backend spec: 13/13 criteria satisfied |

## Test status (last recorded)
- Keyboard navigation: 71 tests (32 unit + 39 integration), integration 39/39; real-time updates: 75/75; ProblemDetail: 51/51; `App.test.tsx`: 2/2.
- Re-run `npm test` and `./gradlew test` for current numbers — these were recorded at the time of each task.

## Product flow (receipt QR, customer PWA, admin screens)

| Area | Result |
|---|---|
| Order ↔ session | Orders may be created without a session; the server starts one and the receipt QR links to `/track/{sessionId}` |
| Customer PWA | `/track/:sessionId`: live status (SSE + polling), wait time, Web Push opt-in, uk/en, manifest + service worker |
| Staff | "New order" dialog with printable QR; role-based navigation (staff / restaurant admin / platform admin) |
| Admin | Staff management, restaurant name, platform restaurant onboarding; fake metrics and stub pages removed |
| Reliability | Async push with retries, session housekeeping, Flyway migrations, Redis login throttle, SSE heartbeat |
| Delivery | Dockerfile, docker-compose, CI, Prometheus metrics, `scripts/smoke.sh`; see [DEPLOYMENT.md](DEPLOYMENT.md) |

## Open items
- Web Push has not been verified on real devices (Android Chrome, iOS Safari 16.4+ as an installed PWA) with real VAPID keys.
- The SSE stream still takes the staff token as a query parameter (`/api/orders/stream?token=`); replace it with a short-lived single-use ticket.
- A counter QR (one per restaurant, customer enters the order number) is not built; only the per-receipt QR exists.
- No browser E2E suite (Playwright); `scripts/smoke.sh` covers the API flow only.
- 7 react-refresh lint warnings (contexts that also export hooks).
- Real-device mobile and real screen-reader testing (only emulation / automated tests were done).
- Accessibility gaps from the screen-reader audit — see [UI_QUALITY.md](UI_QUALITY.md).
