# Dashboard UI Theme Switcher - Specification

## Quick Reference

**Feature**: `dashboard-ui-theme-switcher`  
**Technology Stack**: React 18 + TypeScript 5 + Tailwind CSS 3 + shadcn/ui  
**Type**: Web UI Dashboard with Theme Support  
**Status**: Design-First Workflow  
**Target Users**: Restaurant staff (operations team)  

---

## Document Structure

This specification includes:

1. **design.md** - Comprehensive technical design covering:
   - Architecture and component hierarchy
   - Light/dark color schemes with contrast ratios
   - Component interfaces and data models
   - Layout structure (desktop, tablet, mobile)
   - CSS variables and Tailwind configuration
   - Error handling, testing, performance, security
   - Correctness properties (10 properties for verification)

2. **requirements.md** - Formal requirements with acceptance criteria:
   - 12 main requirement sections (theme, layout, responsive, a11y, real-time, search, performance, errors, security, testing, browser support, i18n)
   - 60+ acceptance criteria defining specific behaviors
   - Priority levels (P0 blocking, P1 important, P2 nice-to-have)

3. **tasks.md** - Implementation roadmap:
   - 57 actionable tasks across 13 phases
   - Dependency ordering for parallel work
   - Time estimates (~116 hours, 2-3 weeks)
   - Prioritized task list and breakdowns

---

## Key Features

### 1. Theme Management
- **Light Mode**: Neutral colors with blue accents (#0066CC primary)
- **Dark Mode**: Dark background with bright accents (#60A5FA primary)
- **Persistence**: localStorage saves user preference (`readybell-theme`)
- **System Detection**: Respects `prefers-color-scheme` on first visit
- **Instant Switching**: All UI updates immediately on theme toggle

### 2. Dashboard Components
- **Header**: Search, notifications, theme toggle, settings
- **Sidebar**: 7-item navigation menu + user profile
- **Order Cards**: Summary metrics (Total, New, Preparing, Ready)
- **Order Table**: Paginated, sortable, filterable list
- **Real-Time Updates**: SSE integration for live order changes

### 3. Responsive Design
- **Mobile** (<768px): Drawer sidebar, stacked cards, horizontal scroll table
- **Tablet** (768-1023px): Collapsed sidebar, 2-column cards
- **Desktop** (1024px+): Full sidebar, 4-column cards, full table

### 4. Accessibility
- **WCAG 2.1 Level AA** compliance
- **Keyboard Navigation**: Full tab/arrow support, skip links
- **Screen Reader Ready**: Semantic HTML, ARIA labels, live regions
- **Color Contrast**: 4.5:1 for text, 3:1 for UI components in both themes

### 5. Performance
- **Code Splitting**: Lazy-loaded pages and components
- **Bundle Targets**: <150KB (main app gzipped)
- **Core Web Vitals**: LCP <2.5s, FCP <1.5s, CLS <0.1
- **Lighthouse Score**: ≥85 Performance, ≥95 Accessibility

---

## Architecture Overview

```
┌─────────────────────────────────────────┐
│         ThemeProvider                   │
│  (Context + localStorage + CSS vars)    │
└────────────┬────────────────────────────┘
             │
    ┌────────┴──────────────┐
    │                       │
┌───▼─────────┐       ┌────▼────────┐
│   Header    │       │   Sidebar   │
│  + Toggle   │       │  + Profile  │
└─────────────┘       └─────────────┘
    │                       │
    └───────────┬───────────┘
                │
        ┌───────▼──────────┐
        │  Main Content    │
        │  ┌────────────┐  │
        │  │Card Grid   │  │
        │  └────────────┘  │
        │  ┌────────────┐  │
        │  │Order Table │  │
        │  └────────────┘  │
        └──────────────────┘
```

---

## Color System

### Light Theme
```
Primary:     #0066CC (blue)
Secondary:   #F0F4FF (light blue bg)
Accent:      #FF6B35 (orange)
Background:  #FFFFFF (white)
Surface:     #F8F9FA (light gray)
Border:      #E0E0E0 (light gray)

Text:
  Primary:   #1A1A1A (black)
  Secondary: #666666 (gray)
  Muted:     #999999 (light gray)
```

### Dark Theme
```
Primary:     #60A5FA (bright blue)
Secondary:   #1E3A5F (dark blue bg)
Accent:      #FF8A50 (lighter orange)
Background:  #0F1419 (near-black)
Surface:     #1A202C (dark gray)
Border:      #374151 (medium gray)

Text:
  Primary:   #F3F4F6 (near-white)
  Secondary: #D1D5DB (light gray)
  Muted:     #9CA3AF (medium gray)
```

---

## Component Checklist

- [ ] ThemeProvider - State management & persistence
- [ ] useTheme Hook - Context access
- [ ] ThemeToggle - Button component
- [ ] Header - Top navigation bar
- [ ] Sidebar - Left navigation menu
- [ ] Layout - Grid structure wrapper
- [ ] OrderSummaryCard - Metric display card
- [ ] OrderMetricsGrid - 4-card grid
- [ ] IncomingOrdersTable - Main data table
- [ ] OrderStatusBadge - Status color/label
- [ ] SearchInput - Debounced search
- [ ] StatusFilter - Multi-select filter
- [ ] DateRangeFilter - Date picker filter
- [ ] TableSkeleton - Loading state
- [ ] EmptyState - No data message
- [ ] ErrorBoundary - Error handling wrapper
- [ ] useSSE Hook - Real-time connection
- [ ] useOrderUpdates Hook - Update subscription

---

## API Integration Points

## API Integration Points

### Backend Endpoints (Verified from Backend Spec)

The frontend consumes these verified backend endpoints from `restaurant-notification-platform` spec:

**Customer Session API (PWA)**
```
POST  /api/sessions                      - Start session from QR token
GET   /api/sessions/{id}/orders          - List orders for session
```

**Order Management API (Panel - Staff Auth)**
```
GET   /api/orders                        - Fetch orders list (paginated, filterable)
POST  /api/orders                        - Create order for session
PATCH /api/orders/{id}/status            - Update order status
GET   /api/orders/stream                 - SSE for live order updates
GET   /api/orders/poll                   - Polling fallback (ETag-based)
```

**Subscription API (PWA)**
```
POST  /api/sessions/{id}/subscriptions   - Register/update push subscription
DELETE /api/sessions/{id}/subscriptions/{subId} - Revoke subscription
```

### Order Status Enum (Backend Definition)

Backend defines these exact statuses (from domain model):
- `CREATED` - New order just created
- `PREPARING` - Kitchen is preparing
- `READY` - Ready for pickup
- `COMPLETED` - Order completed
- `CANCELLED` - Order cancelled

Frontend MUST use exact enum values. Status colors defined in design.md.

### Data Models Alignment

- **OrderRow** maps to backend Order entity
- **OrderMetrics** calculated from order list (not a separate endpoint)
- **Pagination** uses standard offset (page + pageSize)
- **SSE stream** publishes OrderStatusUpdated + OrderCreated events
- **ETag header** supports conditional polling requests

### Error Handling

Backend returns RFC 7807 `application/problem+json` with error codes and details. Frontend treats these as structured errors, not just HTTP status codes.

### Mock Data Available
- 20-30 sample orders with varying statuses
- Metrics: totals, trends, status breakdowns
- User profile data
- Filter options

---

## Testing Requirements

| Type | Target | Status |
|------|--------|--------|
| Unit Tests | ≥85% coverage | To implement |
| Integration Tests | Core flows | To implement |
| Accessibility Tests | Zero violations | To implement |
| E2E Tests | Happy path | Optional |
| Performance | Lighthouse ≥85 | To verify |
| Browser Compat | Chrome, Firefox, Safari | To test |
| Responsive | Mobile, Tablet, Desktop | To test |

---

## Development Workflow

### 1. Setup Phase (~16 hours)
- Initialize Vite + React + TypeScript
- Configure Tailwind + shadcn/ui
- Setup theme context and localStorage
- Configure API client and mocking

### 2. Component Building (~32 hours)
- Layout components (Header, Sidebar)
- Content components (Cards, Table)
- Theme toggle integration
- Search/filter components

### 3. Integration (~12 hours)
- Real-time updates (SSE)
- Filter logic integration
- Error handling
- Loading states

### 4. Quality Assurance (~40 hours)
- Unit and integration tests
- Accessibility testing
- Responsive design verification
- Performance optimization

### 5. Release (~16 hours)
- Documentation
- CI/CD setup
- Browser testing
- Production deployment

---

## Key Dependencies

```json
{
  "react": "^18.0.0",
  "typescript": "^5.0.0",
  "tailwindcss": "^3.3.0",
  "@headlessui/react": "^1.7.0",
  "@heroicons/react": "^2.0.0",
  "@tanstack/react-query": "^5.0.0",
  "@tanstack/react-table": "^8.0.0",
  "@testing-library/react": "^14.0.0",
  "vitest": "^0.34.0"
}
```

---

## Success Criteria

✅ **Functional**
- Theme toggle works and persists across sessions
- Dashboard displays all 4 order metrics
- Table shows paginated orders with sorting
- Search and filters work correctly
- Real-time updates appear in table

✅ **Quality**
- All components have unit tests (≥85% coverage)
- Accessibility: WCAG 2.1 AA, zero violations
- Performance: Lighthouse ≥85, Core Web Vitals met
- Browser compatibility: Chrome, Firefox, Safari, Edge latest

✅ **Deployment**
- Builds without errors: `npm run build`
- Deployable to production
- Documentation complete
- Team trained on usage

---

## Stakeholder References

- **Design**: Light/dark theme mockups in design.md
- **Backend Team**: API endpoints needed in requirements.md
- **QA Team**: Test cases in requirements.md and tasks.md
- **DevOps**: Build/deploy steps in tasks.md (Phase 12-13)
- **Product**: Feature requirements in requirements.md

---

## Next Steps

1. **Review** this specification with team
2. **Approve** design and requirements
3. **Start Phase 1** (Project Setup) - Task 1.1
4. **Track** progress in tasks.md (checkbox each task)
5. **Demo** completed features at phase milestones

---

## Revision History

| Date | Version | Changes |
|------|---------|---------|
| 2024 | 1.0 | Initial design-first spec |

---

**Specification Version**: 1.0  
**Created**: 2024  
**Feature**: dashboard-ui-theme-switcher  
**Workflow**: Design-First (Design → Requirements → Tasks)
