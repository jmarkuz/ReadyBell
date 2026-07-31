# Backend Endpoints Integration Guide

## Overview

This document maps the ReadyBell backend API endpoints to the Dashboard UI and provides integration requirements based on the restaurant-notification-platform spec.

**Generated**: 2024  
**Backend Spec**: `.kiro/specs/restaurant-notification-platform/`  
**Frontend**: `dashboard-ui/`

---

## API Architecture

### Authentication
- **Type**: Bearer Token (HTTP Authorization header)
- **Format**: `Authorization: Bearer {token}`
- **Scope**: Staff (`Restaurant_Staff`), Admin (`Restaurant_Admin`), Platform Admin (`Platform_Admin`)
- **Storage**: localStorage key `auth-token` (frontend managed)

### Tenant Isolation
- **Mechanism**: Query-layer filter enforces `tenant_id` matching
- **User Profile**: Each authenticated request includes tenant scope
- **Error**: Cross-tenant access returns 403 Forbidden

### Multi-Tenant Data Model
All domain entities include `tenantId` (UUID):
- `CustomerSession` (public, unauthenticated)
- `Order` (staff/customer scoped)
- `DeviceSubscription` (customer scoped)
- `StaffAccount` (admin scoped)
- `Restaurant` (admin/platform scoped)

---

## Endpoint Reference

### 1. Order Management (Staff Panel - `/api/orders`)

#### 1.1 List Orders (Authenticated Staff)
```
GET /api/orders
Authorization: Bearer {token}

Query Parameters:
  - page: number (default: 1)
  - pageSize: number (default: 25, max: 100)
  - status: string[] (CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED)
  - sortBy: 'id' | 'customerName' | 'eta' | 'status' | 'created' (default: 'created')
  - sortOrder: 'asc' | 'desc' (default: 'desc')
  - search: string (searches orderId, customerName, items)

Response (200 OK):
{
  "items": [
    {
      "id": "uuid",
      "orderId": "string",
      "customerId": "uuid",
      "customerSessionId": "uuid",
      "status": "CREATED" | "IN_PROGRESS" | "READY" | "COMPLETED" | "CANCELLED",
      "items": [
        {
          "menuItemId": "uuid",
          "name": "string",
          "quantity": number,
          "specialInstructions": "string"
        }
      ],
      "createdAt": "ISO8601",
      "updatedAt": "ISO8601",
      "estimatedReadyTime": "ISO8601"
    }
  ],
  "totalCount": number,
  "pageCount": number,
  "currentPage": number
}

Error (400 Bad Request):
{
  "type": "urn:readybell:validation:invalid-filter",
  "title": "Invalid Filter",
  "status": 400,
  "detail": "Invalid status filter value"
}

Error (401 Unauthorized):
{
  "type": "urn:readybell:auth:invalid-token",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid or expired token"
}
```

**UI Implementation**: Dashboard's OrderMetricsGrid + IncomingOrdersTable components
**Current Status**: ✅ Mocked (simplified version)
**Real Backend Ready**: Needs authentication headers added to requests

---

#### 1.2 Create Order
```
POST /api/orders
Authorization: Bearer {token}
Content-Type: application/json

Request Body:
{
  "customerSessionId": "uuid",
  "items": [
    {
      "menuItemId": "uuid",
      "quantity": number,
      "specialInstructions": "string" (optional)
    }
  ]
}

Response (201 Created):
{
  "id": "uuid",
  "orderId": "string",
  "status": "CREATED",
  "customerSessionId": "uuid",
  "createdAt": "ISO8601"
}

Error (400 Bad Request):
{
  "type": "urn:readybell:validation:invalid-request",
  "title": "Invalid Request",
  "status": 400,
  "detail": "Invalid customer session ID"
}

Error (403 Forbidden):
{
  "type": "urn:readybell:auth:tenant-mismatch",
  "title": "Forbidden",
  "status": 403,
  "detail": "Customer session belongs to a different tenant"
}
```

**UI Implementation**: Create Order modal/form (future enhancement)
**Current Status**: ✅ Mocked
**Real Backend Ready**: Needs validation and session lookup

---

#### 1.3 Update Order Status
```
PATCH /api/orders/{orderId}/status
Authorization: Bearer {token}
Content-Type: application/json

Request Body:
{
  "status": "IN_PROGRESS" | "READY" | "COMPLETED" | "CANCELLED"
}

Response (200 OK):
{
  "id": "uuid",
  "orderId": "string",
  "status": "IN_PROGRESS",
  "updatedAt": "ISO8601"
}

Error (400 Bad Request):
{
  "type": "urn:readybell:orders:invalid-transition",
  "title": "Invalid State Transition",
  "status": 400,
  "detail": "Cannot transition from COMPLETED to IN_PROGRESS"
}

Error (404 Not Found):
{
  "type": "urn:readybell:orders:not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Order not found"
}

Error (409 Conflict):
{
  "type": "urn:readybell:orders:concurrent-modification",
  "title": "Conflict",
  "status": 409,
  "detail": "Order was updated concurrently"
}
```

**UI Implementation**: Status dropdown/buttons in OrdersTable
**Current Status**: ✅ Mocked
**Real Backend Ready**: Needs optimistic locking & conflict handling

**State Transitions (Valid)**:
```
CREATED → IN_PROGRESS
CREATED → CANCELLED

IN_PROGRESS → READY
IN_PROGRESS → CANCELLED

READY → COMPLETED
READY → CANCELLED

COMPLETED → (no transitions)
CANCELLED → (no transitions)
```

---

#### 1.4 Server-Sent Events (Order Updates)
```
GET /api/orders/stream
Authorization: Bearer {token}
Accept: text/event-stream

Response (200 OK, Content-Type: text/event-stream):
event: OrderCreated
data: {
  "orderId": "string",
  "status": "CREATED",
  "createdAt": "ISO8601"
}

event: OrderStatusUpdated
data: {
  "orderId": "string",
  "previousStatus": "IN_PROGRESS",
  "status": "READY",
  "updatedAt": "ISO8601"
}

event: OrderReady
data: {
  "orderId": "string",
  "customerName": "string",
  "updatedAt": "ISO8601"
}
```

**UI Implementation**: useSSE hook + live table updates
**Current Status**: ✅ Mocked
**Real Backend Ready**: Needs Bearer token support in fetch-based SSE

---

#### 1.5 Polling Fallback
```
GET /api/orders/poll?since={ISO8601}&eTag={eTag}
Authorization: Bearer {token}

Query Parameters:
  - since: ISO8601 (only return orders modified after this time)
  - eTag: string (conditional request, return 304 if unchanged)

Response (200 OK):
{
  "orders": [
    {
      "id": "uuid",
      "orderId": "string",
      "status": "READY",
      "updatedAt": "ISO8601"
    }
  ],
  "eTag": "string"
}
Headers:
  ETag: "string"

Response (304 Not Modified):
(empty body)
Headers:
  ETag: "string"
```

**UI Implementation**: useOrderPolling hook (SSE fallback)
**Current Status**: ✅ Mocked
**Real Backend Ready**: Needs ETag generation + conditional logic

---

### 2. Restaurant Staff Admin (`/api/admin/staff`)

#### 2.1 Create Staff Account
```
POST /api/admin/staff
Authorization: Bearer {adminToken}
Content-Type: application/json

Request Body:
{
  "username": "string",
  "email": "string",
  "password": "string",
  "name": "string",
  "role": "STAFF" | "ADMIN"
}

Response (201 Created):
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "name": "string",
  "role": "STAFF",
  "status": "ACTIVE",
  "createdAt": "ISO8601"
}

Error (400 Bad Request):
{
  "type": "urn:readybell:validation:invalid-request",
  "title": "Invalid Request",
  "status": 400,
  "detail": "Username already exists"
}
```

**UI Implementation**: Admin panel (staff management page)
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Needs implementation

---

#### 2.2 Deactivate Staff Account
```
DELETE /api/admin/staff/{accountId}
Authorization: Bearer {adminToken}

Response (204 No Content):
(empty body)

Error (404 Not Found):
{
  "type": "urn:readybell:admin:staff-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Staff account not found"
}
```

**UI Implementation**: Admin panel (staff management)
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Needs implementation

---

### 3. Restaurant Configuration (`/api/admin/config`)

#### 3.1 Update Restaurant Config
```
PUT /api/admin/config
Authorization: Bearer {adminToken}
Content-Type: application/json

Request Body:
{
  "restaurantName": "string",
  "phoneNumber": "string",
  "address": "string",
  "cuisineType": "string",
  "averagePrepTime": number (seconds),
  "webhookUrl": "string" (optional)
}

Response (200 OK):
{
  "id": "uuid",
  "restaurantName": "string",
  "phoneNumber": "string",
  "address": "string",
  "cuisineType": "string",
  "averagePrepTime": number,
  "webhookUrl": "string",
  "updatedAt": "ISO8601"
}
```

**UI Implementation**: Admin settings page
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Needs implementation

---

### 4. Platform Admin (`/api/platform/restaurants`)

#### 4.1 Create Restaurant Tenant
```
POST /api/platform/restaurants
Authorization: Bearer {platformAdminToken}
Content-Type: application/json

Request Body:
{
  "restaurantName": "string",
  "ownerEmail": "string",
  "ownerName": "string"
}

Response (201 Created):
{
  "tenantId": "uuid",
  "restaurantName": "string",
  "status": "ACTIVE",
  "createdAt": "ISO8601"
}
```

**UI Implementation**: Platform admin panel
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Needs implementation

---

#### 4.2 Update Restaurant (Platform Admin)
```
PUT /api/platform/restaurants/{tenantId}
Authorization: Bearer {platformAdminToken}
Content-Type: application/json

Request Body:
{
  "restaurantName": "string",
  "status": "ACTIVE" | "INACTIVE"
}

Response (200 OK):
{
  "tenantId": "uuid",
  "restaurantName": "string",
  "status": "ACTIVE",
  "updatedAt": "ISO8601"
}
```

**UI Implementation**: Platform admin panel
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Needs implementation

---

#### 4.3 Deactivate Restaurant
```
DELETE /api/platform/restaurants/{tenantId}
Authorization: Bearer {platformAdminToken}

Response (204 No Content):
(empty body)
```

**UI Implementation**: Platform admin panel
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Needs implementation

---

### 5. Customer Session (Public - `/api/sessions`)

#### 5.1 Start Customer Session (QR Code)
```
POST /api/sessions
Content-Type: application/json

Request Body:
{
  "qrToken": "string" (encoded restaurant tenant + session metadata)
}

Response (201 Created):
{
  "sessionId": "uuid",
  "tenantId": "uuid",
  "restaurantName": "string",
  "status": "ACTIVE",
  "createdAt": "ISO8601",
  "expiresAt": "ISO8601"
}

Error (400 Bad Request):
{
  "type": "urn:readybell:session:invalid-qr",
  "title": "Invalid QR Code",
  "status": 400,
  "detail": "QR code is invalid or expired"
}
```

**UI Implementation**: Customer PWA (future)
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Not applicable (PWA only)

---

#### 5.2 List Customer Session Orders
```
GET /api/sessions/{sessionId}/orders
(no authentication required)

Response (200 OK):
{
  "sessionId": "uuid",
  "orders": [
    {
      "id": "uuid",
      "orderId": "string",
      "status": "CREATED" | "IN_PROGRESS" | "READY" | "COMPLETED" | "CANCELLED",
      "items": [
        { "name": "string", "quantity": number }
      ],
      "createdAt": "ISO8601",
      "estimatedReadyTime": "ISO8601"
    }
  ]
}
```

**UI Implementation**: Customer PWA (future)
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Not applicable (PWA only)

---

### 6. Web Push Subscriptions (`/api/sessions/{sessionId}/subscriptions`)

#### 6.1 Register Device Subscription
```
POST /api/sessions/{sessionId}/subscriptions
Content-Type: application/json

Request Body (Web Push API format):
{
  "endpoint": "string",
  "keys": {
    "p256dh": "string",
    "auth": "string"
  }
}

Response (201 Created):
{
  "subscriptionId": "uuid",
  "endpoint": "string",
  "createdAt": "ISO8601"
}

Error (400 Bad Request):
{
  "type": "urn:readybell:subscription:invalid-data",
  "title": "Invalid Subscription Data",
  "status": 400,
  "detail": "Missing required keys"
}
```

**UI Implementation**: Customer PWA (future)
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Not applicable (PWA only)

---

#### 6.2 Revoke Subscription
```
DELETE /api/sessions/{sessionId}/subscriptions/{subscriptionId}

Response (204 No Content):
(empty body)
```

**UI Implementation**: Customer PWA (future)
**Current Status**: ❌ Not mocked
**Real Backend Ready**: Not applicable (PWA only)

---

## Error Response Format

All errors follow RFC 7807 (Problem Details for HTTP APIs):

```json
{
  "type": "urn:readybell:{context}:{error}",
  "title": "Human Readable Title",
  "status": 400,
  "detail": "Specific error description",
  "instance": "/api/orders/123" (optional)
}
```

**Common Error Types**:
- `urn:readybell:auth:invalid-token` (401)
- `urn:readybell:auth:tenant-mismatch` (403)
- `urn:readybell:validation:invalid-request` (400)
- `urn:readybell:validation:invalid-filter` (400)
- `urn:readybell:orders:not-found` (404)
- `urn:readybell:orders:invalid-transition` (400)
- `urn:readybell:orders:concurrent-modification` (409)
- `urn:readybell:session:invalid-qr` (400)
- `urn:readybell:subscription:invalid-data` (400)

---

## Integration Checklist

### Phase 1: Order Management (In Progress)
- [x] Mock GET `/api/orders` (list with pagination, filtering, sorting)
- [x] Mock POST `/api/orders` (create order)
- [x] Mock PATCH `/api/orders/{id}/status` (update status)
- [x] Mock GET `/api/orders/stream` (SSE)
- [x] Mock GET `/api/orders/poll` (polling fallback)
- [ ] Add Bearer token support to all endpoints
- [ ] Update mock handlers to match backend contract exactly
- [ ] Add validation for state transitions
- [ ] Add tenant_id handling to mock

### Phase 2: Staff Admin (Not Started)
- [ ] Implement POST `/api/admin/staff`
- [ ] Implement DELETE `/api/admin/staff/{id}`
- [ ] Implement PUT `/api/admin/config`
- [ ] Add staff management UI (admin panel)
- [ ] Add config management UI

### Phase 3: Platform Admin (Not Started)
- [ ] Implement POST `/api/platform/restaurants`
- [ ] Implement PUT `/api/platform/restaurants/{id}`
- [ ] Implement DELETE `/api/platform/restaurants/{id}`
- [ ] Add platform admin panel UI

### Phase 4: Customer PWA (Future)
- [ ] Implement POST `/api/sessions` (QR start)
- [ ] Implement GET `/api/sessions/{id}/orders`
- [ ] Implement POST `/api/sessions/{id}/subscriptions`
- [ ] Implement DELETE `/api/sessions/{id}/subscriptions/{id}`
- [ ] Create PWA customer interface

### Phase 5: Authentication & Security (Parallel)
- [ ] Add Bearer token login endpoint
- [ ] Add token refresh logic
- [ ] Add logout & token cleanup
- [ ] Add session expiry handling
- [ ] Add 403 forbidden handling (tenant mismatch)

---

## Configuration

### Environment Variables
```bash
# API Base URL (default: http://localhost:8080)
VITE_API_BASE_URL=http://localhost:8080

# VAPID Keys (Web Push)
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...

# Session Settings
SESSION_TIMEOUT_MINUTES=30
```

### Mock Toggle
To switch between mocked and real backend:

```typescript
// dashboard-ui/src/main.tsx

// Comment out to use real backend:
import { worker } from './mocks/browser';
worker.start();

// Or conditionally:
if (import.meta.env.DEV && !import.meta.env.VITE_USE_REAL_BACKEND) {
  const { worker } = await import('./mocks/browser');
  worker.start();
}
```

---

## Testing Integration

### Unit Tests
```bash
npm run test
```

### Integration Tests (against real backend)
```bash
# Start backend
./gradlew bootRun

# Start frontend
npm run dev

# Run integration tests
npm run test:integration
```

### E2E Tests (Playwright)
```bash
npm run test:e2e
```

---

## Performance Considerations

### Order List Pagination
- Default: 25 items per page
- Max: 100 items per page
- Recommended: Use pagination for lists > 50 items

### Real-Time Updates
- **SSE**: Persistent connection (recommended for modern browsers)
- **Polling**: 10-second interval (fallback for older browsers)
- **Latency Target**: < 2 seconds from state change to UI update

### Caching Strategy
- **ETag-based**: Use `/api/orders/poll` with ETag for conditional requests
- **Client-side**: React Query / SWR for data caching
- **Redis**: Backend uses Redis for session/cache state

---

## Security Checklist

- [x] Use HTTPS in production
- [x] Bearer token in Authorization header
- [x] Tenant isolation enforced at query layer
- [x] Error responses don't leak sensitive data
- [x] CORS headers configured for frontend domain
- [x] CSRF token (if using cookies — not needed with Bearer)
- [ ] Rate limiting on authentication endpoints
- [ ] Audit logging for sensitive operations
- [ ] Regular security scan (OWASP Top 10)

---

## Support & Troubleshooting

### Common Issues

**Issue**: 401 Unauthorized
- **Cause**: Invalid or missing Bearer token
- **Fix**: Check localStorage for `auth-token`, verify token not expired

**Issue**: 403 Forbidden
- **Cause**: Tenant mismatch (accessing another restaurant's data)
- **Fix**: Verify tenant_id in request matches authenticated user's tenant

**Issue**: 409 Conflict
- **Cause**: Concurrent order state transition
- **Fix**: Refresh order state and retry with latest version

**Issue**: SSE Connection Fails
- **Cause**: Bearer token not sent with EventSource
- **Fix**: Use fetch-based SSE (see `useSSE` hook) instead of EventSource

**Issue**: CORS Errors
- **Cause**: Frontend and backend on different domains
- **Fix**: Verify backend CORS headers include frontend domain

---

## Related Documentation

- Backend Requirements: `.kiro/specs/restaurant-notification-platform/requirements.md`
- Backend Design: `.kiro/specs/restaurant-notification-platform/design.md`
- Backend API: `README.md` (API Overview section)
- Frontend Types: `dashboard-ui/src/types/index.ts`
- Frontend API Client: `dashboard-ui/src/utils/api.ts`
- Alignment Verification: `ALIGNMENT_VERIFICATION_REPORT.md`

---

**Last Updated**: 2024  
**Status**: In Progress (Phase 1 - Order Management)  
**Next Review**: After Phase 2 completion
