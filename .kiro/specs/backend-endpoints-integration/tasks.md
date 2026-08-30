# Implementation Tasks: Backend Endpoints Integration

## Overview

This task list implements full backend API integration for the ReadyBell dashboard across all four phases:
- **Phase 1**: Order Management with real-time updates (SSE + polling)
- **Phase 2**: Restaurant Staff Admin (staff accounts + config)
- **Phase 3**: Platform Admin (restaurant tenant management)
- **Phase 4**: Customer PWA prep (infrastructure only)

These tasks build on the existing dashboard-ui-theme-switcher implementation (completed phases 1-9.2) and extend it with real backend integration.

## Completion Criteria

- All 22 backend endpoints from BACKEND_ENDPOINTS_INTEGRATION.md have corresponding UI implementations
- Bearer token authentication works end-to-end
- Real-time updates (SSE + polling fallback) function correctly
- Multi-tenant isolation enforced on UI layer
- RFC 7807 error handling consistent across all endpoints
- All components tested with real backend (not just mocks)
- UI pages created for Phase 2, 3, and 4

---

# Implementation Plan

## Phased Approach

This implementation is structured in 5 phases with wave-based execution to manage dependencies and parallel work:

- **Wave 0** (P0 Blockers): Bearer token auth + RFC 7807 error handling
- **Wave 1** (Real-Time & Query Features): SSE, polling, pagination, multi-tenant isolation (parallel)
- **Wave 2** (Order State Management): Order status transitions with conflict resolution
- **Wave 3** (Admin Navigation): Staff Admin and Platform Admin page structure
- **Wave 4** (Admin Functionality): Staff accounts, restaurant settings, tenant management (parallel)
- **Wave 5** (PWA Infrastructure): Session and push subscription API methods (parallel)
- **Wave 6** (Testing): Integration tests, E2E testing, performance verification (parallel)

See the Wave-Based Task Scheduling section for detailed execution order and dependencies.

## Task Dependency Graph

```
Phase 1: API Client Foundation (BLOCKER)
  Task 1.1: Bearer Token Auth ──┐
  Task 1.2: RFC 7807 Errors ────┤
                                 ├──> Task 1.3: SSE Updates ────┐
                                 ├──> Task 1.4: Polling ────────┤
                                 ├──> Task 1.5: Pagination ─────┤
                                 │                               ├──> Task 1.6: Status Transitions
                                 └──> Task 1.7: Multi-Tenant ───┤
                                                                 ├──> Phase 2 & 3 Admin Pages
                                                                 └──> Phase 4 PWA Infrastructure

Phase 2: Staff Admin Pages
  Task 2.1: Navigation ────> Task 2.2: Staff Accounts
                        \──> Task 2.3: Restaurant Settings

Phase 3: Platform Admin Pages
  Task 3.1: Navigation ────> Task 3.2: Tenant Management

Phase 4: Customer PWA Infrastructure
  Task 4.1: Session API Methods
  Task 4.2: Push Subscription Methods

Phase 5: Testing & Verification
  Task 5.1: Integration Tests
  Task 5.2: E2E Testing (all phases)
  Task 5.3: Performance Verification (all phases)
```

---

## Tasks

### Phase 1: API Client Enhancement & Bearer Token Integration

- [-] 1.1 Implement Bearer Token Authentication in API Client

**Description**: Enhance `src/utils/api.ts` to handle bearer token lifecycle including storage, request injection, token refresh, and logout.

**Acceptance Criteria**:
- ✅ Bearer token stored in secure storage (localStorage or sessionStorage)
- ✅ All API requests include `Authorization: Bearer {token}` header
- ✅ 401 responses trigger token refresh attempt or redirect to login
- ✅ Logout clears stored token and invalidates session
- ✅ Token expiry detected and handled gracefully
- ✅ No sensitive data exposed in console logs

**Sub-tasks**:
- [x] 1.1.1: Create TokenManager class with store/get/clear/validate methods
- [x] 1.1.2: Add request interceptor to inject Bearer token header
- [x] 1.1.3: Add response interceptor to handle 401 responses
- [x] 1.1.4: Implement token refresh logic with retry
- [x] 1.1.5: Create useAuth hook for components to access auth state
- [x] 1.1.6: Add token validation on app initialization
- [x] 1.1.7: Test bearer token flow end-to-end with real backend

**Owner**: Frontend Team  
**Priority**: P0 (Blocker for all other integrations)  
**Effort**: 3 points

---

- [~] 1.2 Implement RFC 7807 Error Handling in API Client

**Description**: Enhance error handling to parse and process Problem Detail responses consistently across all endpoints.

**Acceptance Criteria**:
- ✅ All error responses parsed as RFC 7807 Problem Details
- ✅ Error type, title, status, and detail fields extracted
- ✅ User-friendly error messages displayed in UI
- ✅ 401, 403, 404, 409, 5xx errors handled with specific logic
- ✅ Sensitive data not exposed in error messages
- ✅ Error logging configured for debugging

**Sub-tasks**:
- [x] 1.2.1: Create ProblemDetail interface and parser
- [~] 1.2.2: Implement error handler for all HTTP status codes
- [~] 1.2.3: Map backend error types to user-friendly messages
- [~] 1.2.4: Add retry logic for 5xx errors (exponential backoff)
- [~] 1.2.5: Create ErrorBoundary component for UI error display
- [~] 1.2.6: Test error handling with mock error scenarios

**Owner**: Frontend Team  
**Priority**: P0  
**Effort**: 2 points

---

- [~] 1.3 Implement SSE (Server-Sent Events) Real-Time Updates

**Description**: Create `useSSE` hook to establish persistent SSE connection for real-time order updates with auto-reconnect.

**Acceptance Criteria**:
- ✅ SSE connection establishes on orders page mount
- ✅ Order events received and parsed (OrderCreated, OrderStatusUpdated, OrderReady)
- ✅ Local order state updated within 2 seconds of backend change
- ✅ Connection auto-reconnects with exponential backoff on failure
- ✅ Heartbeat detects dead connections and triggers reconnect
- ✅ Event deduplication prevents duplicate updates

**Sub-tasks**:
- [~] 1.3.1: Create useSSE hook with EventSource API
- [~] 1.3.2: Implement connection lifecycle (open/error/close)
- [~] 1.3.3: Add auto-reconnect with exponential backoff (1s, 2s, 4s, max 30s)
- [~] 1.3.4: Implement heartbeat detection (30s interval, 60s timeout)
- [~] 1.3.5: Add event deduplication logic
- [~] 1.3.6: Sync full order list on reconnect to recover missed events
- [~] 1.3.7: Test SSE with real backend, verify latency < 2 seconds

**Owner**: Frontend Team  
**Priority**: P0  
**Effort**: 4 points

---

- [~] 1.4 Implement Polling Fallback for Real-Time Updates

**Description**: Create `useOrderPolling` hook as fallback when SSE is unavailable, using ETag-based conditional requests.

**Acceptance Criteria**:
- ✅ Polling starts when SSE fails or is unavailable
- ✅ Poll interval configurable (10s for dashboard, 5s for mobile)
- ✅ ETag support reduces bandwidth (304 Not Modified responses)
- ✅ Polling stops when SSE reconnects
- ✅ No duplicate events from polling + SSE combo

**Sub-tasks**:
- [~] 1.4.1: Create useOrderPolling hook with configurable interval
- [~] 1.4.2: Implement ETag request/response handling
- [~] 1.4.3: Handle 304 Not Modified responses
- [~] 1.4.4: Detect SSE failure and start polling fallback
- [~] 1.4.5: Stop polling when SSE reconnects
- [~] 1.4.6: Add deduplication to prevent duplicate SSE + polling updates
- [~] 1.4.7: Test polling behavior with real backend

**Owner**: Frontend Team  
**Priority**: P0  
**Effort**: 3 points

---

- [~] 1.5 Enhance Order List with Pagination, Filtering, and Sorting

**Description**: Extend IncomingOrdersTable component to support backend query parameters for pagination, filtering, sorting.

**Acceptance Criteria**:
- ✅ Pagination: page, pageSize (default 25, max 100) parameters working
- ✅ Filtering: status filter, search by orderId/customerName working
- ✅ Sorting: sortBy (id, customerName, eta, status, created) and sortOrder
- ✅ Filter/sort state preserved in URL query parameters
- ✅ Backend returns totalCount, pageCount, currentPage
- ✅ Table updates when filters/page/sort change

**Sub-tasks**:
- [~] 1.5.1: Update API client getOrders() to accept query parameters
- [~] 1.5.2: Add pagination controls (prev/next, page selector, size selector)
- [~] 1.5.3: Add status filter dropdown (multi-select checkboxes)
- [~] 1.5.4: Add search input with debounce
- [~] 1.5.5: Add sort controls (click column header to sort)
- [~] 1.5.6: Sync filter/sort state to URL query parameters
- [~] 1.5.7: Test pagination, filtering, sorting with real backend data

**Owner**: Frontend Team  
**Priority**: P0  
**Effort**: 3 points

---

- [~] 1.6 Implement Order Status Transitions with Conflict Detection

**Description**: Enhance order status update logic to validate transitions, detect concurrent modifications (409 Conflict), and resolve conflicts.

**Acceptance Criteria**:
- ✅ Valid transitions enforced (CREATED→IN_PROGRESS→READY→COMPLETED)
- ✅ Invalid transitions blocked client-side with error message
- ✅ 409 Conflict responses trigger conflict resolution dialog
- ✅ Conflict resolution shows current backend state and retry/refresh options
- ✅ Optimistic updates revert on failure
- ✅ Concurrent modifications detected and handled

**Sub-tasks**:
- [~] 1.6.1: Implement order state machine validation (VALID_TRANSITIONS map)
- [~] 1.6.2: Update IncomingOrdersTable actions column with context menu
- [~] 1.6.3: Implement status dropdown showing only valid next states
- [~] 1.6.4: Create optimistic update logic (immediate UI update, revert on failure)
- [~] 1.6.5: Create ConflictResolutionDialog component
- [~] 1.6.6: Handle 409 responses with conflict resolution flow
- [~] 1.6.7: Test optimistic updates and conflict scenarios

**Owner**: Frontend Team  
**Priority**: P0  
**Effort**: 3 points

---

- [~] 1.7 Implement Multi-Tenant Data Isolation Validation

**Description**: Add client-side tenant context validation to prevent accidental cross-tenant access and enforce authorization checks.

**Acceptance Criteria**:
- ✅ Tenant context extracted from Bearer token
- ✅ All API requests validated against tenant context before sending
- ✅ Cross-tenant access blocked with 403 error displayed
- ✅ Role-based access control enforced (STAFF, ADMIN, PLATFORM_ADMIN)
- ✅ Role claims validated from token on initialization

**Sub-tasks**:
- [~] 1.7.1: Create getTenantContext() function to extract tenant_id and role from token
- [~] 1.7.2: Add request validator middleware to check tenant context
- [~] 1.7.3: Implement role-based navigation (show/hide page links based on role)
- [~] 1.7.4: Add authorization check before sensitive operations
- [~] 1.7.5: Handle 403 Forbidden responses (tenant mismatch)
- [~] 1.7.6: Create AccessDenied component for blocked access
- [~] 1.7.7: Test cross-tenant access blocking

**Owner**: Frontend Team  
**Priority**: P0  
**Effort**: 2 points

---

### Phase 2: Restaurant Staff Admin Pages

- [~] 2.1 Create Staff Admin Navigation and Page Structure

**Description**: Add Staff Admin section to navigation menu and create main Staff Admin page with two sections: Staff Accounts and Restaurant Settings.

**Acceptance Criteria**:
- ✅ Staff Admin link appears in sidebar for ADMIN role
- ✅ Staff Admin page displays two sections: Staff Accounts and Restaurant Settings
- ✅ Breadcrumb navigation shows "Dashboard > Staff Admin > [Section]"
- ✅ Page structure matches design doc mockup
- ✅ Layout applies theme correctly

**Sub-tasks**:
- [~] 2.1.1: Update sidebar navigation to include Staff Admin link (ADMIN role only)
- [~] 2.1.2: Create /src/pages/AdminStaff.tsx page component
- [~] 2.1.3: Create Breadcrumb component for navigation context
- [~] 2.1.4: Add Staff Accounts section placeholder
- [~] 2.1.5: Add Restaurant Settings section placeholder
- [~] 2.1.6: Test navigation to Staff Admin page, verify theme applies

**Owner**: Frontend Team  
**Priority**: P1  
**Effort**: 2 points

---

- [~] 2.2 Implement Staff Accounts Management UI

**Description**: Create Staff Accounts section with table listing staff members and modal for creating new accounts.

**Acceptance Criteria**:
- ✅ Staff accounts displayed in table with columns: username, email, name, role, status, action
- ✅ Create Staff Account button opens form modal
- ✅ Form validates: username (required), email (required, valid format), password (required), name (required), role (STAFF/ADMIN dropdown)
- ✅ Form submission sends POST /api/admin/staff
- ✅ Success: account added to table, success toast shown
- ✅ Error: validation errors displayed in form, error toast shown
- ✅ Deactivate button prompts confirmation then sends DELETE request

**Sub-tasks**:
- [~] 2.2.1: Create StaffAccountsTable component with mock staff data structure
- [~] 2.2.2: Add columns: username, email, name, role (STAFF/ADMIN), status (ACTIVE/INACTIVE)
- [~] 2.2.3: Implement Deactivate action with confirmation dialog
- [~] 2.2.4: Create CreateStaffModal component with form
- [~] 2.2.5: Implement form validation (email format, required fields)
- [~] 2.2.6: Connect form to API: POST /api/admin/staff
- [~] 2.2.7: Add error handling for duplicate username, invalid email, etc.
- [~] 2.2.8: Refresh table after successful create/deactivate
- [~] 2.2.9: Test with real backend API

**Owner**: Frontend Team  
**Priority**: P1  
**Effort**: 4 points

---

- [~] 2.3 Implement Restaurant Settings Management UI

**Description**: Create Restaurant Settings section with form to view and update restaurant configuration.

**Acceptance Criteria**:
- ✅ Form displays current settings: restaurantName, phoneNumber, address, cuisineType, averagePrepTime, webhookUrl
- ✅ Form fields are editable
- ✅ Save button validates and sends PUT /api/admin/config
- ✅ Success: success toast shown, form updates with new values
- ✅ Error: validation errors displayed, error toast shown
- ✅ Cancel button discards changes
- ✅ Form loads initial settings from GET /api/admin/config

**Sub-tasks**:
- [~] 2.3.1: Create RestaurantSettingsForm component
- [~] 2.3.2: Add form fields: name, phone, address, cuisine, prepTime (number), webhook URL (optional)
- [~] 2.3.3: Add form validation (non-empty name, valid phone format, valid URL for webhook)
- [~] 2.3.4: Implement form submission: PUT /api/admin/config
- [~] 2.3.5: Load initial settings on component mount: GET /api/admin/config
- [~] 2.3.6: Handle API errors and display in form
- [~] 2.3.7: Implement Cancel button to discard changes
- [~] 2.3.8: Test form with real backend API

**Owner**: Frontend Team  
**Priority**: P1  
**Effort**: 3 points

---

### Phase 3: Platform Admin Pages

- [~] 3.1 Create Platform Admin Navigation and Page Structure

**Description**: Add Platform Admin section to navigation menu and create main Platform Admin page with Restaurant Management section.

**Acceptance Criteria**:
- ✅ Platform Admin link appears in sidebar for PLATFORM_ADMIN role only
- ✅ Platform Admin page displays Restaurant Management section
- ✅ Breadcrumb navigation shows "Dashboard > Platform Admin > Restaurants"
- ✅ Page structure matches design doc mockup
- ✅ Layout applies theme correctly

**Sub-tasks**:
- [~] 3.1.1: Update sidebar navigation to include Platform Admin link (PLATFORM_ADMIN role only)
- [~] 3.1.2: Create /src/pages/AdminPlatform.tsx page component
- [~] 3.1.3: Add Restaurant Management section placeholder
- [~] 3.1.4: Test navigation to Platform Admin page, verify theme applies

**Owner**: Frontend Team  
**Priority**: P1  
**Effort**: 1 point

---

- [~] 3.2 Implement Restaurant Tenant Management UI

**Description**: Create Restaurant Management section with table listing all restaurants and modals for creating/editing restaurants.

**Acceptance Criteria**:
- ✅ Restaurants displayed in table with columns: tenantId, restaurantName, status, createdAt, actions
- ✅ Create Restaurant button opens form modal
- ✅ Create form: restaurantName (required), ownerEmail (required, valid), ownerName (required)
- ✅ Create success: restaurant added to table, tenantId shown in toast, success toast
- ✅ Edit button opens form pre-filled with current data
- ✅ Edit form: restaurantName (required), status (ACTIVE/INACTIVE radio)
- ✅ Deactivate button prompts confirmation, sends DELETE request
- ✅ Error handling for all operations

**Sub-tasks**:
- [~] 3.2.1: Create RestaurantTenantTable component showing all restaurants
- [~] 3.2.2: Add columns: tenantId, restaurantName, status, createdAt
- [~] 3.2.3: Add action buttons: Edit, Deactivate
- [~] 3.2.4: Create CreateRestaurantModal with form
- [~] 3.2.5: Implement form validation for create (email format, required fields)
- [~] 3.2.6: Connect create to API: POST /api/platform/restaurants
- [~] 3.2.7: Create EditRestaurantModal with pre-filled data
- [~] 3.2.8: Connect edit to API: PUT /api/platform/restaurants/{tenantId}
- [~] 3.2.9: Implement Deactivate with confirmation: DELETE /api/platform/restaurants/{tenantId}
- [~] 3.2.10: Add loading states and error handling
- [~] 3.2.11: Refresh table after successful operations
- [~] 3.2.12: Test with real backend API

**Owner**: Frontend Team  
**Priority**: P1  
**Effort**: 5 points

---

### Phase 4: Customer PWA Infrastructure (Preparation Only)

- [~] 4.1 Prepare Customer Session API Client Methods

**Description**: Implement API client methods for customer session management (no UI, infrastructure only).

**Acceptance Criteria**:
- ✅ API methods created for POST /api/sessions (start session), GET /api/sessions/{id}/orders
- ✅ Methods handle QR token parsing
- ✅ Methods return typed response (CustomerSession, Order list)
- ✅ Error handling for invalid QR codes
- ✅ No UI pages created (deferred to Phase 4 PWA spec)

**Sub-tasks**:
- [~] 4.1.1: Create startCustomerSession(qrToken) API method
- [~] 4.1.2: Create getSessionOrders(sessionId) API method
- [~] 4.1.3: Define CustomerSession and SessionOrder types
- [~] 4.1.4: Test API methods with mocked endpoints

**Owner**: Frontend Team  
**Priority**: P2  
**Effort**: 1 point

---

- [~] 4.2 Prepare Web Push Subscription API Client Methods

**Description**: Implement API client methods for push subscription management (no UI, infrastructure only).

**Acceptance Criteria**:
- ✅ API methods created for POST /api/sessions/{id}/subscriptions (register), DELETE (revoke)
- ✅ Methods handle Web Push API subscription objects
- ✅ Error handling for invalid subscription data
- ✅ No UI pages created (deferred to Phase 4 PWA spec)

**Sub-tasks**:
- [~] 4.2.1: Create registerPushSubscription(sessionId, subscription) API method
- [~] 4.2.2: Create revokePushSubscription(sessionId, subscriptionId) API method
- [~] 4.2.3: Define PushSubscription and DeviceSubscription types
- [~] 4.2.4: Test API methods with mocked endpoints

**Owner**: Frontend Team  
**Priority**: P2  
**Effort**: 1 point

---

### Phase 5: Testing & Verification

- [~] 5.1 Integration Test Suite for API Client

**Description**: Write integration tests for all API client methods with real backend mocked responses.

**Acceptance Criteria**:
- ✅ Tests for all 22 endpoints
- ✅ Tests for authentication flow (login, token refresh, logout)
- ✅ Tests for error handling (401, 403, 404, 409, 5xx)
- ✅ Tests for real-time updates (SSE, polling)
- ✅ Tests for multi-tenant isolation
- ✅ All tests passing

**Sub-tasks**:
- [~] 5.1.1: Create test fixtures for API responses
- [~] 5.1.2: Write tests for authentication endpoints
- [~] 5.1.3: Write tests for order endpoints (get, create, update)
- [~] 5.1.4: Write tests for staff admin endpoints
- [~] 5.1.5: Write tests for platform admin endpoints
- [~] 5.1.6: Write tests for error scenarios
- [~] 5.1.7: Run test suite and verify coverage

**Owner**: Frontend Team  
**Priority**: P1  
**Effort**: 3 points

---

- [~] 5.2 End-to-End Testing with Real Backend

**Description**: Test all implemented features with the real backend running locally.

**Acceptance Criteria**:
- ✅ Backend running on localhost:8080
- ✅ Bearer token authentication working end-to-end
- ✅ Order list loads and displays correctly
- ✅ Real-time updates work (SSE or polling)
- ✅ Pagination, filtering, sorting work
- ✅ Order status transitions work
- ✅ Staff admin pages work (if backend implemented)
- ✅ Platform admin pages work (if backend implemented)
- ✅ Error handling works (401, 403, 404, 409)
- ✅ Multi-tenant isolation enforced

**Sub-tasks**:
- [~] 5.2.1: Start backend service on localhost:8080
- [~] 5.2.2: Test bearer token login and session
- [~] 5.2.3: Test order list page with real data
- [~] 5.2.4: Test real-time updates (watch for live changes)
- [~] 5.2.5: Test pagination, filtering, sorting
- [~] 5.2.6: Test order status transitions
- [~] 5.2.7: Test staff admin pages (if backend ready)
- [~] 5.2.8: Test platform admin pages (if backend ready)
- [~] 5.2.9: Test error scenarios (invalid token, cross-tenant, etc.)
- [~] 5.2.10: Document any issues found

**Owner**: Frontend Team  
**Priority**: P0  
**Effort**: 4 points

---

- [~] 5.3 Performance Verification

**Description**: Verify performance targets are met for real-time updates and API response times.

**Acceptance Criteria**:
- ✅ Real-time update latency < 2 seconds (measured from backend change to UI update)
- ✅ Order list page loads in < 1 second (first page)
- ✅ Search/filter/sort results return in < 1 second
- ✅ SSE heartbeat maintains connection (30s interval)
- ✅ Polling fallback works efficiently with ETag (10s interval)
- ✅ Bundle size remains within targets
- ✅ Lighthouse performance score ≥ 85

**Sub-tasks**:
- [~] 5.3.1: Measure real-time update latency with stopwatch/timing tools
- [~] 5.3.2: Measure page load time for orders list
- [~] 5.3.3: Measure search/filter/sort response times
- [~] 5.3.4: Monitor SSE heartbeat and reconnection
- [~] 5.3.5: Monitor polling fallback behavior
- [~] 5.3.6: Run Lighthouse audit (build + analyze)
- [~] 5.3.7: Document performance metrics

**Owner**: Frontend Team  
**Priority**: P1  
**Effort**: 2 points

---

## Wave-Based Task Scheduling

```json
{
  "waves": [
    {
      "wave": 0,
      "name": "API Client Foundation (P0 Blockers)",
      "parallel": true,
      "tasks": [
        { "id": "1.1", "name": "Bearer Token Auth" },
        { "id": "1.2", "name": "RFC 7807 Error Handling" }
      ]
    },
    {
      "wave": 1,
      "name": "Real-Time & Query Features",
      "parallel": true,
      "dependsOn": ["1.1", "1.2"],
      "tasks": [
        { "id": "1.3", "name": "SSE Updates" },
        { "id": "1.4", "name": "Polling Fallback" },
        { "id": "1.5", "name": "Pagination/Filter/Sort" },
        { "id": "1.7", "name": "Multi-Tenant Isolation" }
      ]
    },
    {
      "wave": 2,
      "name": "Order Status Transitions",
      "parallel": false,
      "dependsOn": ["1.5"],
      "tasks": [
        { "id": "1.6", "name": "Status Transitions & Conflict Resolution" }
      ]
    },
    {
      "wave": 3,
      "name": "Admin Pages",
      "parallel": true,
      "dependsOn": ["1.7"],
      "tasks": [
        { "id": "2.1", "name": "Staff Admin Navigation" },
        { "id": "3.1", "name": "Platform Admin Navigation" }
      ]
    },
    {
      "wave": 4,
      "name": "Admin Functionality",
      "parallel": true,
      "dependsOn": ["2.1", "3.1"],
      "tasks": [
        { "id": "2.2", "name": "Staff Account Management" },
        { "id": "2.3", "name": "Restaurant Settings" },
        { "id": "3.2", "name": "Tenant Management" }
      ]
    },
    {
      "wave": 5,
      "name": "PWA Infrastructure",
      "parallel": true,
      "dependsOn": ["1.1"],
      "tasks": [
        { "id": "4.1", "name": "Session API Methods" },
        { "id": "4.2", "name": "Push Subscription Methods" }
      ]
    },
    {
      "wave": 6,
      "name": "Testing & Verification",
      "parallel": true,
      "dependsOn": ["2.3", "3.2", "4.2"],
      "tasks": [
        { "id": "5.1", "name": "Integration Test Suite" },
        { "id": "5.2", "name": "E2E Testing with Real Backend" },
        { "id": "5.3", "name": "Performance Verification" }
      ]
    }
  ]
}
```

---

## Metrics & Success Criteria

| Metric | Target | Status |
|--------|--------|--------|
| Bearer token authentication working | ✅ Must have | — |
| Real-time update latency | < 2 seconds | — |
| Order list page load | < 1 second | — |
| Search/filter/sort response | < 1 second | — |
| Integration test coverage | ≥ 85% | — |
| Lighthouse performance score | ≥ 85 | — |
| Multi-tenant isolation verified | ✅ Must have | — |
| RFC 7807 error handling | ✅ Must have | — |
| Phase 2 admin pages functional | ✅ Should have | — |
| Phase 3 admin pages functional | ✅ Should have | — |
| Phase 4 PWA infrastructure | ✅ Nice to have | — |

---

## Time Estimates

- **Phase 1** (API Client): 5-6 days (1.1-1.7 in parallel/sequence)
- **Phase 2** (Staff Admin): 3-4 days (2.1-2.3)
- **Phase 3** (Platform Admin): 2-3 days (3.1-3.2)
- **Phase 4** (PWA Prep): 1 day (4.1-4.2)
- **Phase 5** (Testing): 3-4 days (5.1-5.3)

**Total**: ~14-20 days (depends on parallel execution and backend readiness)

---

## Prerequisites & Blockers

- Backend running on localhost:8080 with all 22 endpoints implemented
- Bearer token authentication endpoint available (POST /api/auth/login)
- MSW mock handlers updated to match backend responses exactly (or disabled for real backend)
- Backend CORS configured to accept frontend requests from localhost:5173

---

## Notes

### Key Implementation Principles

- **Wave 0 is Critical**: Both Task 1.1 (Bearer Token Auth) and Task 1.2 (RFC 7807 Error Handling) must complete before any other phase can begin. These are hard blockers.
- **Real-Time Priority**: Tasks 1.3 and 1.4 (SSE + Polling) are the highest priority after auth. Order updates should reflect backend changes within 2 seconds.
- **Multi-Tenant Enforcement**: Task 1.7 adds crucial security validation on the client side. This protects against accidental cross-tenant data access.
- **Admin Pages are Secondary**: Tasks in Phases 2 and 3 depend on Phase 1 completion. They can run in parallel once Phase 1 is done.
- **Testing is Final Gate**: Phase 5 testing validates all implementation work. E2E testing with the real backend (Task 5.2) is the ultimate verification step.

### Risk Factors

- **Backend Readiness**: All tasks depend on backend endpoints being implemented and running. If backend is delayed, frontend work stalls.
- **Token Refresh Logic**: Task 1.1.4 is complex—token refresh with concurrent request handling can cause race conditions if not careful.
- **SSE Connection Stability**: Task 1.3 requires solid auto-reconnect logic. Network instability testing is critical.
- **Conflict Resolution**: Task 1.6 requires careful state management. Optimistic updates reverting on failure can confuse users if not handled smoothly.

### Testing Strategy

- All unit tests should run within the task's scope (sub-tasks 1.x.x, 2.x.x, etc.)
- Integration tests in Task 5.1 combine multiple components
- E2E testing in Task 5.2 validates against real backend
- Performance verification in Task 5.3 ensures targets are met

---

**Last Updated**: 2024  
**Status**: Ready for implementation  
**Next Step**: Execute Wave 0 (Task 1.1 + Task 1.2)
