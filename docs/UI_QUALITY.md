# Dashboard UI — Accessibility, Responsive & Performance Reference

Results of tasks 8.x and 9.x of the theme-switcher spec (details of the removed per-task reports are in git history).
Contrast values are in `dashboard-ui/src/utils/colorContrast.ts` tests.

## Colour palette and contrast (WCAG 2.1 AA)

Requirements: normal text ≥ 4.5:1, large text / UI components / focus indicators ≥ 3:1.
Formula: WebAIM relative luminance, ratio = (L1+0.05)/(L2+0.05).

### Light theme (bg `#FFFFFF`, surface `#F8F9FA`)
| Use | Colour | on bg | on surface |
|-----|--------|-------|-----------|
| Primary text | `#1A1A1A` | 21.0 | 11.0 |
| Secondary / muted text | `#666666` | 5.7 | **3.9 (marginal, below 4.5)** |
| Primary / focus ring | `#0066CC` (hover `#0052A3`) | 7.2 (8.9) | – |
| Success | `#0B855C` | 6.1 | 5.2 |
| Warning | `#B45309` | 8.8 | 7.4 |
| Error | `#DC2626` | 4.6 | 3.9 |
| Info | `#2563EB` | 5.1 | 4.3 |
| Border | `#E0E0E0` | 2.0 | 1.1 (decorative only) |

### Dark theme (bg `#0F1419`, surface `#1A202C`)
| Use | Colour | on bg | on surface |
|-----|--------|-------|-----------|
| Primary text | `#F3F4F6` | 12.5 | 9.2 |
| Secondary text | `#D1D5DB` | 9.2 | 6.9 |
| Muted text | `#9CA3AF` | 7.0 | – |
| Primary / focus ring | `#60A5FA` (hover `#93C5FD`) | 6.1 (8.2) | 5.2 |
| Success | `#34D399` | 9.1 | 7.8 |
| Warning | `#FBBF24` | 12.8 | 10.9 |
| Error | `#F87171` | 10.2 | 8.8 |
| Border | `#374151` | 2.8 | 1.3 (decorative only) |

### Components
- Primary button: white on `#0066CC` 7.2; `#0F1419` on `#60A5FA` 6.1. Secondary button 7.2 / 6.1. Disabled `#999999` on white 4.5.
- Input focus border 7.2 / 6.1; error message 4.6 (light) / 10.2 (dark).
- Status badges (text/bg, light → dark): CREATED `#2563EB` 5.1 → 6.1; PREPARING `#B45309` 8.8 → 12.8; READY `#0B855C` 6.1 → 9.1;
  COMPLETED `#6B7280` 5.0 → `#9CA3AF` **3.5**; CANCELLED `#DC2626` 4.6 → 10.2.
- Known caveats: secondary text on the light surface (3.9), error/info on the light surface (3.9 / 4.3) and COMPLETED badge in dark mode (3.5)
  pass only the 3:1 non-text threshold. Revisit if used for body text.

## Semantic HTML & ARIA (task 8.1)
- Landmarks: `header[role=banner]`, `aside[aria-label="Main navigation"]` + `nav`, `main#main-content`, `role=search`; sidebar overlay `aria-hidden`.
- Header: theme toggle `aria-label` (switch to light/dark), settings menu with `aria-haspopup="menu"` + `aria-expanded`, notification badge `role=status`.
- `OrderSummaryCard` is a `<button>` with full `aria-label` ("Title: 42 up 12% vs. last hour"), decorative icons `aria-hidden`, loading `role=status`.
- Status badges carry `aria-label`/`role`; table has semantic structure; focus rings `focus:ring-2 focus:ring-blue-500 focus:ring-offset-2`.

## Keyboard navigation (task 8.2)
`KeyboardNavigation.unit.test.tsx` (32 tests) and `KeyboardNavigation.integration.test.tsx` (39 tests).
Initially 28/39 integration tests passed — the 11 failures were missing Router context in the test setup; fixed afterwards (39/39).

## Screen-reader audit (task 8.3) — open gaps
Audit result: 7 of 10 criteria met. Gaps found (verify whether closed — `aria-current` and a skip link are **not** present in `src/` at the time of writing):
1. **High**: `aria-sort` on `IncomingOrdersTable` headers did not update (stayed "none") on sorting.
2. **High**: skip-to-main-content link missing in `Layout.tsx`.
3. **High**: no `aria-current="page"` on the active `Sidebar` NavLink.
4. Medium: real-time order updates not announced (needs `aria-live="polite"`; only `PageLoader`, `Dashboard`, `AuthProvider` have live regions).
5. Medium: inconsistent heading hierarchy (h2/h3) on Dashboard and other pages.
6. Medium: manual testing with real screen readers (NVDA / VoiceOver) not done.
7. Low: richer ARIA descriptions, landmark verification, focus-trap check for dialogs.

## Responsive design (task 8.5)
Tested at 320 / 768 / 1024+ px with Chrome DevTools emulation (no real devices).

| | Mobile 320 | Tablet 768 | Desktop ≥1024 |
|--|-----------|-----------|--------------|
| Sidebar | drawer overlay opened by menu button | collapsed 80 px, expands on hover (`md:w-20 md:hover:w-64`) | always visible, full width |
| Header | compact, logo text hidden, search in compact form | full controls | full |
| Summary cards | 1 column | 2×2 | 4 columns |
| Table | horizontal scroll inside the table only | readable | full width |

All breakpoints: text ≥ 14 px, touch targets ≥ 44×44 px, no page-level horizontal scroll.

## Theme persistence (tasks 1.4, 3.2, 8.6)
- `ThemeContext` stores the choice in `localStorage` key `readybell-theme`; invalid value → `light`; first visit uses `prefers-color-scheme`.
- Verified in emulated iPhone Safari / Android Chrome. Safari Private Mode still supports `localStorage` but it is cleared when the private session ends.
- Enhancement not done: react to live `prefers-color-scheme` changes after the first visit.

## Performance (tasks 9.1, 9.2)
**Code splitting** — `React.lazy` + `Suspense` for every page (`App.tsx`); `vite.config.ts` manual chunks:
`vendor-react` (react, react-dom), `vendor-ui` (lucide-react, clsx, date-fns), `vendor-router`, `app-shared` (context, utils), `page-<name>`;
terser with `drop_console`, sourcemaps, `chunkSizeWarningLimit: 1000`.

| Chunk | Raw | Gzip |
|-------|-----|------|
| vendor-react | 177.1 KB | 57.75 KB (target < 150 KB) |
| page-dashboard | 33.7 KB | 7.69 KB |
| vendor-ui | 16.7 KB | 6.09 KB |
| index | 15.6 KB | 4.37 KB |
| app-shared | 1.9 KB | 0.90 KB |
| other pages | ~0.4 KB each | ~0.27 KB |
| CSS | 24.9 KB | 5.29 KB (target < 30 KB) |

Initial load ≈ 73.5 KB gzip (target < 100 KB); total JS ≈ 82 KB gzip (target < 250 KB).

**Memoization** — `React.memo` on `Header`, `OrderSummaryCard`, `OrderStatusBadge`, `OrderMetricsGrid`, `Sidebar`, `IncomingOrdersTable`;
`useMemo` for the metric-card config, sorted orders, paginated orders, Dashboard filtered orders and metrics;
`useCallback` for Header handlers (menu toggle, debounced search), table sort handler, Dashboard filter handlers.
Estimated re-render reduction 50–87 % in test scenarios (theme change intentionally re-renders everything). Verify with React DevTools Profiler.
