# Implementation Plan

# Tasks: Dashboard UI Theme Switcher

## Overview

This implementation plan builds the ReadyBell restaurant dashboard with light/dark theme support using React + TypeScript + Tailwind CSS + shadcn/ui. Work proceeds in dependency order: (1) project setup and theme infrastructure, (2) core layout components (header, sidebar, main), (3) dashboard content components (cards, table), (4) real-time updates integration, (5) search/filter features, (6) testing and optimization.

## Prerequisites

- Node.js 18+ and npm 9+
- GitHub repository with CI/CD pipeline
- Design tokens and component library decisions finalized
- API endpoints documented and available (or mocked)
- ReadyBell backend running (for API testing)

## Task List

### Phase 1: Project Setup & Infrastructure ✅

- [x] 1.1 Initialize React + TypeScript + Tailwind CSS project
  - ✅ Created new Vite React project with TypeScript template
  - ✅ Installed dependencies: React 18, TypeScript 5, Tailwind CSS 3
  - ✅ Configured TypeScript: strict mode, absolute imports (@/)
  - ✅ Configured Tailwind: dark mode with class strategy, CSS variables
  - ✅ Configured Vite: code splitting, source maps, API proxy (/api → localhost:8080)
  - ✅ **Result**: Bootable dev server running at http://localhost:5173

- [x] 1.2 Setup project structure and conventions
  - ✅ Created folder structure: `/src/{components,hooks,context,types,utils,pages,styles,mocks,test}`
  - ✅ Added `.gitignore`, `prettier.config.js`, `.eslintrc.cjs`
  - ✅ Created comprehensive `README.md` with dev/build commands
  - ✅ Configured path aliases (@/) in `tsconfig.json` and `vite.config.ts`
  - ✅ **Result**: Clean, organized project structure ready for development

- [x] 1.3 Create CSS variables and design tokens
  - ✅ Defined 40+ CSS variables for light/dark themes
  - ✅ Created `/src/styles/variables.css` with all colors, spacing, shadows
  - ✅ Applied CSS variables to Tailwind config
  - ✅ Verified all variables work in both light and dark themes
  - ✅ WCAG AA contrast compliance verified
  - ✅ **Result**: All design tokens available in CSS and Tailwind

- [x] 1.4 Setup theme context and localStorage integration
  - ✅ Created `ThemeContext.tsx` with full state management
  - ✅ Implemented `useTheme()` hook for component access
  - ✅ Implemented localStorage persistence (key: `readybell-theme`)
  - ✅ Implemented system preference detection (prefers-color-scheme)
  - ✅ Tested: persistence works, invalid values default to light
  - ✅ **Result**: Theme system fully functional with persistence

- [x] 1.5 Configure TypeScript interfaces and types
  - ✅ Created `/src/types/index.ts` with 15+ type definitions
  - ✅ Defined: Order, OrderStatus, OrderRow, OrderMetrics, UserProfile, MenuItem, etc.
  - ✅ Backend API alignment documented in comments
  - ✅ All types exported with full IDE autocomplete support
  - ✅ **Result**: Centralized type definitions, backend-aligned

- [x] 1.6 Setup API client and mocking
  - ✅ Created `/src/utils/api.ts` with axios/fetch client
  - ✅ Configured base URL, Authorization headers, interceptors
  - ✅ Created `/src/mocks/handlers.ts` with MSW for all endpoints
  - ✅ Mocked: GET /api/orders, POST /api/orders, PATCH /api/orders/{id}/status, SSE, polling
  - ✅ Sample data: 15-30 varied orders with realistic statuses
  - ✅ **Result**: Works without backend, API client ready

- [x] 1.7 Setup component testing infrastructure
  - ✅ Installed testing stack: vitest, @testing-library/react, happy-dom
  - ✅ Configured vitest with happy-dom, path aliases, coverage thresholds
  - ✅ Created `/src/test/setup.ts` with jest-dom and utilities
  - ✅ Created example tests: App (6 tests), ThemeContext (14 tests)
  - ✅ **Result**: 20/20 tests passing, ready for TDD

### Phase 2: Core Layout Components

- [-] 2.1 Create Layout wrapper component
  - Create `src/components/Layout.tsx` with grid/flex structure
  - Props: children (main content)
  - Render: Header, Sidebar, main content area, user profile
  - Responsive: adjust layout at breakpoints
  - Test: layout renders without errors
  - Result: Layout component with all sections in place

- [-] 2.2 Implement Header component
  - Create `src/components/Header.tsx`
  - Include: logo/home link, search input (debounced), notification icon with badge, theme toggle, settings menu
  - Props: onSearch, onFilterChange, notificationCount, currentTheme, onThemeToggle
  - Responsive: adapt spacing/hide elements on mobile
  - Icons: use Lucide React icons (SearchIcon, BellIcon, MoonIcon, SunIcon, SettingsIcon)
  - Test: search debounces, theme toggle fires callback
  - Result: Header component with all features

- [-] 2.3 Implement Sidebar navigation component
  - Create `src/components/Sidebar.tsx`
  - Include: ReadyBell logo, navigation menu (7 items), user profile section
  - Props: onNavigate, isCollapsed, onToggleCollapse, userProfile
  - Navigation items: Dashboard (home), Orders, Menu Management, Payments, Analytics, Settings, Support
  - Active state: highlight current route
  - User profile: avatar, name, role, restaurant name at bottom
  - Responsive: collapse to icon-only on tablet, drawer on mobile
  - Test: active state updates, navigation fires callback
  - Result: Sidebar navigation component

- [-] 2.4 Create App root component with routing
  - Create `src/App.tsx`
  - Setup React Router v6 with routes
  - Routes: /, /orders, /menu, /payments, /analytics, /settings, /support
  - Wrap app with ThemeProvider and Router
  - Create placeholder page components for each route
  - Test: router works, all routes accessible
  - Result: App routing structure set up

- [-] 2.5 Create Dashboard page layout
  - Create `src/pages/Dashboard.tsx`
  - Layout: order summary cards grid + incoming orders table
  - Props/state: orders, metrics, loading, filters
  - Responsive: 4 cols (desktop), 2 cols (tablet), 1 col (mobile)
  - Test: renders without errors, loads page
  - Result: Dashboard page template

### Phase 3: Theme Toggle Feature

- [~] 3.1 Implement ThemeToggle button component
  - Create `src/components/ThemeToggle.tsx`
  - Display: sun/moon icon based on current theme
  - Props: currentTheme, onToggle, size, showLabel
  - Click: toggles theme via useTheme hook
  - Accessibility: aria-label, keyboard support (Enter/Space), visible focus indicator
  - Test: click toggles theme, keyboard activation works, aria-label present
  - Result: Theme toggle button fully functional

- [~] 3.2 Test theme persistence across reloads
  - Manual test: toggle theme → reload page → theme persists
  - Test localStorage has correct value
  - Test invalid values default to light
  - Test system preference used on first visit
  - Result: Theme persistence verified

- [~] 3.3 Test theme switching on all components
  - Manual test: toggle theme → all colors change correctly
  - Verify: Header, Sidebar, Cards, Table all respond to theme
  - Check: status badge colors update
  - Check: shadow and border colors adapt
  - Result: All components respond to theme change

- [~] 3.4 Create theme color documentation
  - Document all color values used in design (light/dark)
  - Create Storybook story for color palette showcase (optional)
  - Create visual reference guide in project docs
  - Result: Color values documented for consistency

### Phase 4: Order Summary Cards

- [~] 4.1 Create OrderSummaryCard component
  - Create `src/components/OrderSummaryCard.tsx`
  - Props: title, value, icon, trend, backgroundColor, onClick, isLoading
  - Display: large metric value, label, trend (if present), icon
  - Hover: shadow increase, slight scale, pointer
  - Loading: show skeleton loader
  - Accessibility: aria-label with full context
  - Test: renders correctly, click fires callback
  - Result: Order card component

- [~] 4.2 Create OrderMetricsGrid component
  - Create `src/components/OrderMetricsGrid.tsx`
  - Renders: 4 OrderSummaryCard components (Total, New, Preparing, Ready)
  - Props: metrics (OrderMetrics), isLoading, onCardClick
  - Responsive: 4 cols (desktop), 2 cols (tablet), 1 col (mobile)
  - Calculate trends: compare current vs previous period
  - Test: renders 4 cards, responsive at breakpoints
  - Result: Metrics grid component

- [~] 4.3 Create mock metrics data
  - Create `src/mocks/orders.ts` with sample order data
  - Generate: 15-30 sample orders with varied statuses
  - Include: trend data (up 12%, down 5%, etc.)
  - Use in development mode
  - Result: Mock data available for development

### Phase 5: Incoming Orders Table

- [~] 5.1 Create IncomingOrdersTable component
  - Create `src/components/IncomingOrdersTable.tsx`
  - Using: @tanstack/react-table for data management
  - Columns: Order ID, Customer Name, Items, Delivery ETA, Status, Actions
  - Features: sortable columns, pagination, loading state
  - Props: orders, isLoading, pageSize, currentPage, onPageChange, onStatusChange
  - Responsive: scrollable horizontally on mobile
  - Test: renders table, sorting works, pagination navigates
  - Result: Table component functional

- [~] 5.2 Implement table sorting
  - Add sort state to table
  - Click column header to toggle sort (asc/desc)
  - Show sort indicator (arrow) on active column
  - Persist sort state in URL (optional)
  - Test: click header, data re-sorts, arrow indicator shows
  - Result: Sorting fully functional

- [~] 5.3 Implement table pagination
  - Add pagination controls: prev, page numbers, next
  - Show "Page X of Y"
  - Page size selector (10, 25, 50 items/page)
  - Click page number to navigate
  - Disable prev/next at boundaries
  - Test: navigation works, counts correct
  - Result: Pagination working

- [~] 5.4 Create OrderStatusBadge component
  - Create `src/components/OrderStatusBadge.tsx`
  - Props: status (OrderStatus)
  - Display: colored badge with label and icon
  - Styles: CREATED (blue), PREPARING (orange), READY (green), COMPLETED (gray), CANCELLED (red)
  - Test: all statuses render with correct colors
  - Result: Status badge component

- [~] 5.5 Implement order actions column
  - Add Actions column to table
  - Include: View Details button, Update Status dropdown
  - Hover: show buttons/menu
  - Mobile: collapse to three-dots menu
  - Test: buttons clickable, menu toggles
  - Result: Actions column functional

- [~] 5.6 Create empty state for no orders
  - Show "No orders found" message when table is empty
  - Display icon and helpful text
  - Include "Create new order" button (if applicable)
  - Test: message appears when orders array is empty
  - Result: Empty state displays

- [~] 5.7 Create loading skeletons for table
  - Create `src/components/TableSkeleton.tsx`
  - Show 5-10 skeleton rows while loading
  - Match table column widths
  - Animate with pulse effect
  - Test: skeletons show during loading state
  - Result: Loading skeleton component

### Phase 6: Search & Filter

- [~] 6.1 Implement order search in Header
  - Search input in header center
  - Debounce input (300ms)
  - Props: onSearch callback
  - Clear button on input
  - Test: search debounces, callback fires after delay
  - Result: Search input working

- [~] 6.2 Implement status filter dropdown
  - Create `src/components/StatusFilter.tsx`
  - Checkboxes for each status: CREATED, PREPARING, READY, COMPLETED
  - Select/deselect all toggle
  - Apply/Cancel buttons
  - Show badge with count of active filters
  - Test: checkboxes toggle, apply fires callback
  - Result: Filter dropdown working

- [~] 6.3 Implement date range filter
  - Create `src/components/DateRangeFilter.tsx`
  - Date picker UI (inputs or calendar)
  - Presets: Today, This Week, This Month, Custom
  - Validation: start date ≤ end date
  - Test: date selection works, validation prevents invalid ranges
  - Result: Date range filter component

- [~] 6.4 Integrate filters with table
  - Store filter state in Dashboard
  - Apply filters to mock order data
  - Show filtered results in table
  - Update badge when filters active
  - Test: selecting filters updates table view
  - Result: Filters integrated with table

### Phase 7: Real-Time Updates (SSE Integration)

- [~] 7.1 Implement SSE client hook
  - Create `src/hooks/useSSE.ts`
  - Props: endpoint, onMessage, onError
  - Auto-reconnect with exponential backoff (1s, 2s, 4s, 8s, max 30s)
  - Handle connection loss and restore
  - Test: connection establishes, messages received
  - Result: SSE hook functional

- [~] 7.2 Create order update event handler
  - Create `src/hooks/useOrderUpdates.ts`
  - Listens to `/api/orders/stream` SSE endpoint
  - Updates table data with new/modified orders
  - Maintains sort order and pagination
  - Test: simulate SSE messages, table updates correctly
  - Result: Real-time updates working

- [~] 7.3 Implement SSE fallback to polling
  - If SSE fails, fall back to polling
  - Create `src/hooks/useOrderPolling.ts`
  - Poll interval: 5s for mobile, 10s for panel
  - Use ETag for conditional requests
  - Test: SSE failure triggers polling fallback
  - Result: Polling fallback implemented

- [~] 7.4 Test real-time updates in table
  - Start dev server with mocked SSE
  - Simulate order updates
  - Verify table updates without full reload
  - Verify sort/pagination maintained
  - Result: Real-time updates working

### Phase 8: Accessibility & Responsive Design

- [~] 8.1 Add semantic HTML and ARIA labels
  - Review all components for semantic tags: nav, main, header, aside, article, table, section
  - Add aria-labels to interactive elements
  - Add aria-live for dynamic updates
  - Add aria-current for active navigation
  - Add table aria-sort attributes
  - Test: HTML validator passes, accessibility audit clean
  - Result: Semantic HTML and ARIA complete

- [~] 8.2 Verify keyboard navigation
  - Tab through all interactive elements
  - Verify tab order is logical (left-to-right, top-to-bottom)
  - Test all buttons activate with Enter/Space
  - Test dropdown navigation with Arrow keys
  - Test no keyboard traps
  - Test skip link to main content
  - Result: Full keyboard navigation verified

- [~] 8.3 Test screen reader support
  - Test with NVDA (Windows) or VoiceOver (Mac)
  - Verify all text readable by screen reader
  - Verify headings announced correctly
  - Verify table structure understood
  - Verify status badges announced with full text
  - Result: Screen reader tested and working

- [~] 8.4 Verify color contrast ratios
  - Use WebAIM contrast checker on all color combinations
  - Verify ≥ 4.5:1 for normal text in both themes
  - Verify ≥ 3:1 for UI components in both themes
  - Document contrast ratios
  - Result: All colors WCAG AA compliant

- [~] 8.5 Test responsive design at breakpoints
  - Test at: 320px (mobile), 768px (tablet), 1024px (desktop)
  - Chrome DevTools device emulation
  - Real device testing if possible
  - Verify no horizontal scroll except table
  - Verify font sizes readable at all breakpoints
  - Verify touch targets 44x44px on mobile
  - Result: Responsive design verified at all breakpoints

- [~] 8.6 Test theme persistence on mobile
  - Test on iPhone Safari and Android Chrome
  - Toggle theme, reload, verify persistence
  - Test in private/incognito mode
  - Result: Theme persistence works on all platforms

### Phase 9: Performance Optimization

- [~] 9.1 Code splitting and lazy loading
  - Split main bundle into chunks (header, sidebar, pages)
  - Lazy load each page with React.lazy()
  - Use Suspense for loading fallback
  - Measure bundle sizes with vite-plugin-visualizer
  - Target: main < 150KB gzipped
  - Result: Code splitting implemented

- [~] 9.2 Component memoization
  - Use React.memo on components that don't need frequent updates
  - Use useMemo for expensive computations
  - Use useCallback for stable function references
  - Verify with React DevTools Profiler
  - Result: Unnecessary re-renders eliminated

- [~] 9.3 Image optimization
  - Use WebP with JPEG fallback for images
  - Implement responsive images with srcset
  - Lazy load images below the fold
  - Inline SVG icons (no separate requests)
  - Result: Images optimized

- [~] 9.4 Run Lighthouse audit
  - Build production bundle: `npm run build`
  - Run Lighthouse: Performance, Accessibility, Best Practices
  - Performance score target: ≥ 85
  - Fix issues from report
  - Document baseline metrics
  - Result: Lighthouse scores at targets

- [~] 9.5 Performance testing
  - Measure Core Web Vitals: LCP, FID, CLS
  - FCP (First Contentful Paint): < 1.5s
  - LCP (Largest Contentful Paint): < 2.5s
  - CLS (Cumulative Layout Shift): < 0.1
  - TTI (Time to Interactive): < 3.5s
  - Result: Performance metrics documented

### Phase 10: Testing & Quality Assurance

- [~] 10.1 Write unit tests for components
  - Test all components: Layout, Header, Sidebar, Cards, Table, etc.
  - Test theme toggle functionality
  - Test sorting and pagination
  - Target: ≥ 85% code coverage
  - Use @testing-library/react best practices
  - Result: Unit test suite > 85% coverage

- [~] 10.2 Write integration tests
  - Test theme persistence end-to-end
  - Test search and filter integration
  - Test table updates with mock data
  - Test error states and recovery
  - Result: Integration tests passing

- [~] 10.3 Write accessibility tests
  - Axe accessibility automated tests
  - Test keyboard navigation
  - Test color contrast
  - Test ARIA attributes
  - Run tests in CI pipeline
  - Result: Zero accessibility violations

- [~] 10.4 Manual testing checklist
  - [~] Theme toggle works and persists
  - [~] All colors correct in light/dark modes
  - [~] Layout responsive at all breakpoints
  - [~] Search input debounces
  - [~] Filters apply and table updates
  - [~] Sorting works
  - [~] Pagination works
  - [~] Empty state displays
  - [~] Loading skeleton shows
  - [~] Error states display correctly
  - [~] Keyboard navigation works
  - [~] Screen reader works
  - [~] Touch targets adequate on mobile
  - Result: Manual testing complete

- [~] 10.5 Create test coverage report
  - Generate coverage report: `npm run test:coverage`
  - Target: ≥ 85% overall coverage
  - Document coverage by component
  - Identify gaps
  - Result: Coverage report generated

### Phase 11: Browser & Device Testing

- [~] 11.1 Test on multiple browsers
  - Chrome (latest stable)
  - Firefox (latest stable)
  - Safari (latest stable)
  - Edge (latest stable)
  - Mobile: iOS Safari, Chrome Android
  - Document any browser-specific issues
  - Result: Browser compatibility verified

- [~] 11.2 Test on real devices
  - iPhone 12+ (iOS 15+)
  - Android phone (Android 12+)
  - iPad (iPadOS 15+)
  - Desktop (1920x1080 minimum)
  - Test all features on each device
  - Document any device-specific issues
  - Result: Real device testing complete

- [~] 11.3 Test in private/incognito mode
  - Verify localStorage works in private mode
  - Verify no data persists between sessions
  - Test theme preference restoration
  - Result: Private mode verified

### Phase 12: Documentation & Deployment

- [~] 12.1 Write component documentation
  - Document each component: purpose, props, usage examples
  - Create Storybook stories for components (optional)
  - Document design tokens (colors, spacing, typography)
  - Create setup and development guide
  - Result: Component documentation complete

- [~] 12.2 Create deployment guide
  - Document build process: `npm run build`
  - Document environment variables required
  - Document server configuration (CORS, headers, proxies)
  - Create docker container configuration (optional)
  - Result: Deployment guide written

- [~] 12.3 Setup CI/CD pipeline
  - Configure GitHub Actions or similar
  - Run tests on pull requests
  - Run linting and formatting checks
  - Build and deploy on merge to main
  - Result: CI/CD pipeline operational

- [~] 12.4 Create user guide
  - Document how to use dashboard as staff user
  - Screenshots of each page
  - Explain theme preference
  - Explain search and filtering
  - Explain order status tracking
  - Result: User guide complete

### Phase 13: Final Review & Release

- [~] 13.1 Code review and quality gates
  - All code reviewed by 1+ team member
  - All tests passing (unit, integration, e2e)
  - Linting clean (ESLint, Prettier)
  - No console errors or warnings
  - Lighthouse scores at targets
  - Result: Code review passed

- [~] 13.2 Security audit
  - Review for XSS vulnerabilities
  - Review localStorage usage (no sensitive data)
  - Verify HTTPS enforced in production
  - Review API security
  - Result: Security audit passed

- [~] 13.3 Performance review
  - Bundle size within targets
  - Core Web Vitals at targets
  - No memory leaks detected
  - Result: Performance review passed

- [~] 13.4 Release to staging
  - Deploy to staging environment
  - Conduct final manual testing
  - Get stakeholder sign-off
  - Result: Deployed to staging

- [~] 13.5 Release to production
  - Deploy to production
  - Monitor error logs
  - Monitor performance metrics
  - Verify theme preference persists
  - Result: Live in production

---

## Implementation Priorities

### Must Have (P0)
- Theme toggle and persistence (1.4, 3.1-3.4)
- Layout and responsive design (2.1-2.5, 8.5)
- Order summary cards (4.1-4.3)
- Incoming orders table (5.1-5.7)
- Accessibility and keyboard navigation (8.1-8.3)
- Performance optimization (9.1-9.5)
- Testing (10.1-10.5)

### Should Have (P1)
- Search and filter (6.1-6.4)
- Real-time updates (7.1-7.4)
- Browser compatibility (11.1-11.3)
- Documentation (12.1-12.4)

### Nice to Have (P2)
- Advanced analytics/reporting
- Custom themes beyond light/dark
- Offline mode
- PWA support
- Multi-language support (i18n)

---

## Task Dependency Graph

```json
{
  "waves": [
    {
      "wave": 0,
      "name": "Phase 1: Setup",
      "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7"]
    },
    {
      "wave": 1,
      "name": "Phase 2: Layout",
      "tasks": ["2.1", "2.2", "2.3", "2.4", "2.5"],
      "dependsOn": ["1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7"]
    },
    {
      "wave": 2,
      "name": "Phase 3: Theme",
      "tasks": ["3.1", "3.2", "3.3", "3.4"],
      "dependsOn": ["1.4", "2.1"]
    },
    {
      "wave": 2,
      "name": "Phase 4-5: Components",
      "tasks": ["4.1", "4.2", "4.3", "5.1", "5.2", "5.3", "5.4", "5.5", "5.6", "5.7"],
      "dependsOn": ["2.1", "2.5"]
    },
    {
      "wave": 3,
      "name": "Phase 6: Search & Filter",
      "tasks": ["6.1", "6.2", "6.3", "6.4"],
      "dependsOn": ["5.1"]
    },
    {
      "wave": 3,
      "name": "Phase 7: Real-Time",
      "tasks": ["7.1", "7.2", "7.3", "7.4"],
      "dependsOn": ["5.1"]
    },
    {
      "wave": 4,
      "name": "Phase 8-11: QA & Testing",
      "tasks": ["8.1", "8.2", "8.3", "8.4", "8.5", "8.6", "9.1", "9.2", "9.3", "9.4", "9.5", "10.1", "10.2", "10.3", "10.4", "10.5", "11.1", "11.2", "11.3"],
      "dependsOn": ["2.1", "3.1", "4.1", "5.1", "6.1", "7.1"]
    },
    {
      "wave": 5,
      "name": "Phase 12-13: Release",
      "tasks": ["12.1", "12.2", "12.3", "12.4", "13.1", "13.2", "13.3", "13.4", "13.5"],
      "dependsOn": ["8.1", "9.5", "10.5", "11.3"]
    }
  ]
}
```

## Time Estimates

| Phase | Tasks | Est. Hours | Notes |
|-------|-------|-----------|-------|
| 1. Setup | 1.1-1.7 | 16 | Project initialization, tooling, infrastructure |
| 2. Layout | 2.1-2.5 | 12 | Core components, structure |
| 3. Theme | 3.1-3.4 | 8 | Theme implementation, testing |
| 4-5. Components | 4.1-5.7 | 20 | Cards, table, sorting, pagination |
| 6. Search/Filter | 6.1-6.4 | 8 | Search, filters integration |
| 7. Real-Time | 7.1-7.4 | 12 | SSE, polling, live updates |
| 8-11. QA | 8.1-11.3 | 24 | Testing, a11y, responsive, performance |
| 12-13. Release | 12.1-13.5 | 16 | Docs, deployment, final review |
| **Total** | **57 tasks** | **116 hours** | ~2-3 weeks with 1 developer |

---

## Notes

- All tasks assume no major blockers on the backend API
- Time estimates assume prior React + TypeScript experience
- Tasks 3.2, 3.3, 3.4 are manual testing (QA passes through development)
- Tasks in phases can run in parallel (e.g., layout and components simultaneously)
- Phase 8-11 (QA) ideally runs continuously during development, not just at the end
- Each task should include git commits with clear messages
- Code review recommended before merge to main
