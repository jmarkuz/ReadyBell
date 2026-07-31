# Integration Testing Guide

## Quick Start

### 1. Start the Dashboard UI with Mock Backend
```bash
cd dashboard-ui
npm run dev
# Opens http://localhost:5173
```

The mock handlers will automatically intercept all API calls.

### 2. Test Authentication

**With Mock Credentials**:
- Username: `staff@restaurant.com`
- Password: `password`

**Expected Result**: 
- Login succeeds
- Bearer token: `staff-tenant-123-abc123` stored in localStorage
- User sees order dashboard for tenant-123

### 3. Test Order Management

#### List Orders
```bash
# Automatically called when dashboard loads
# Mock returns 100 orders, displays first 25

# Test Pagination:
- Click "Next" button
- Should show next 25 orders

# Test Sorting:
- Click "Order ID" column header
- Orders sort ascending (↑)
- Click again to sort descending (↓)

# Test Filtering:
- Click "Status" filter
- Select "READY"
- Table shows only READY orders

# Test Search:
- Type customer name in search
- Orders filtered in real-time
```

#### Update Order Status
```bash
# In the table, find an order with status "IN_PROGRESS"
# Click the status dropdown
# Select "READY"

# Expected: Status changes in table
# Real backend would trigger notification push
```

---

## Test Scenarios

### Scenario 1: Happy Path (Success)

**Steps**:
1. Start dashboard
2. See 25 orders displayed
3. Filter by status "CREATED"
4. Select an order
5. Update status to "IN_PROGRESS"
6. Verify status updated in table

**Expected**: ✅ All operations succeed, no errors

**Evidence**:
- Orders list loads in < 2s
- Filters apply instantly
- Status updates immediately
- No console errors

---

### Scenario 2: Authentication

**Steps**:
1. Open DevTools → Application → Cookies/LocalStorage
2. Delete `auth-token` key
3. Refresh page
4. Try to access `/api/orders`

**Expected**: 
- Request fails with 401 Unauthorized
- User prompted to login
- Mock shows error: "Invalid or expired token"

**Evidence**:
- Network tab shows 401 response
- Error detail: "urn:readybell:auth:invalid-token"

---

### Scenario 3: Tenant Isolation

**Steps**:
1. Login as `staff@restaurant.com` (tenant-123)
2. See orders for tenant-123
3. Open mock handlers and change tenant filter
4. Access API with different tenant ID

**Expected**:
- Orders are scoped to logged-in tenant
- Cannot access orders from other tenants
- Mock enforces `tenant_id` filter

**Evidence**:
- Order IDs contain tenant ID: `order-tenant-123-{n}`
- All orders belong to same tenant

---

### Scenario 4: Error Handling

#### 4A: Invalid Token
```bash
# DevTools → Console
apiClient.get('/api/orders')  # No auth header
# Expected: 401 Unauthorized error
```

#### 4B: Invalid Filter
```bash
# Mock will reject invalid status filter
# URL: /api/orders?status=INVALID_STATUS
# Expected: 400 Bad Request
# Detail: "Invalid status values: INVALID_STATUS"
```

#### 4C: Pagination Out of Bounds
```bash
# URL: /api/orders?page=-1&pageSize=101
# Expected: 400 Bad Request
# Detail: "Page must be >= 1, pageSize must be 1-100"
```

#### 4D: Network Error
```bash
# Request to /api/orders/error/network
# Expected: Network error (ERR_FAILED)
# UI should show "Connection failed, retry?"
```

---

### Scenario 5: Real-Time Updates (SSE)

**Steps**:
1. Open DevTools → Network tab
2. Filter for XHR requests
3. Refresh dashboard
4. Watch for EventSource connection to `/api/orders/stream`
5. Observe events arriving every 3 seconds

**Expected**:
- EventSource connection established (200)
- New "OrderStatusUpdated" or "OrderCreated" events every 3s
- Events contain orderId, status, timestamp

**Evidence** (in Network tab):
```
GET /api/orders/stream
Status: 200
Content-Type: text/event-stream
Cache-Control: no-cache

Response:
event: OrderStatusUpdated
data: {"orderId":"#50123","status":"READY","updatedAt":"2024-01-15T..."}
```

---

### Scenario 6: Polling Fallback

**Steps**:
1. Disable SSE in browser: go to mock handlers and return error from stream
2. Dashboard should fall back to polling
3. Watch Network tab for repeated requests to `/api/orders/poll`

**Expected**:
- Requests every 10 seconds
- ETag headers for conditional requests
- 304 Not Modified when no changes
- 200 OK with updated orders when changed

**Evidence**:
```
GET /api/orders/poll?eTag=%22abc123%22
Status: 304 (no change) or 200 (change)
ETag: "new-hash"
```

---

## Test Data

### Mock Orders Generated

The mock generates 100 orders with realistic data:

```json
{
  "id": "order-tenant-123-1",
  "orderId": "#10000",
  "customerName": "John Smith",
  "itemsOrdered": "Burger Combo, Caesar Salad",
  "deliveryETA": "2024-01-15T14:30:00Z",
  "status": "IN_PROGRESS",
  "estimatedWaitTime": 5,
  "itemCount": 2
}
```

**Status Distribution** (realistic):
- 20% CREATED (new orders)
- 30% IN_PROGRESS (being prepared)
- 20% READY (waiting for pickup)
- 20% COMPLETED (picked up)
- 10% CANCELLED

**Timestamps**:
- Created: Last 2 hours
- ETA: Varies based on status
- CREATED: ~15 min from now
- IN_PROGRESS: ~10 min from now
- READY: ~1 min from now
- COMPLETED: ~5 min ago

---

## API Testing Checklist

### Authentication ✅
- [ ] Mock login endpoint works
- [ ] Bearer token stored in localStorage
- [ ] 401 returned for missing token
- [ ] 401 returned for invalid token
- [ ] User can logout and token cleared

### Order Listing ✅
- [ ] GET /api/orders returns paginated response
- [ ] Default pagination: page=1, pageSize=25
- [ ] Max pageSize enforced: 100
- [ ] Pagination disabled for negative/zero values
- [ ] Empty orders array when no matches

### Order Filtering ✅
- [ ] Status filter works with valid values
- [ ] Invalid status values rejected (400)
- [ ] Multiple status filters work (OR logic)
- [ ] Empty filter returns all orders

### Order Sorting ✅
- [ ] Sort by orderId (default)
- [ ] Sort by status
- [ ] Sort by ETA
- [ ] Sort by customerName
- [ ] Sort ascending/descending (ASC/DESC)

### Order Updates ✅
- [ ] Valid status transitions allowed
- [ ] Invalid transitions rejected (400)
- [ ] Terminal states (COMPLETED, CANCELLED) cannot transition
- [ ] Concurrent updates handled (409 Conflict)
- [ ] Updated status returned in response

### Real-Time Updates ✅
- [ ] SSE stream established on /api/orders/stream
- [ ] OrderCreated events received
- [ ] OrderStatusUpdated events received
- [ ] ETA polling works as fallback
- [ ] ETag conditional requests work

### Error Responses ✅
- [ ] All errors use RFC 7807 format
- [ ] Error type includes context (urn:readybell:...)
- [ ] Error detail describes issue
- [ ] HTTP status matches error type
- [ ] 401 for auth failures
- [ ] 400 for validation failures
- [ ] 403 for permission failures

### Multi-Tenant ✅
- [ ] Orders scoped to authenticated tenant
- [ ] Tenant ID in all order IDs
- [ ] Cannot access other tenant's orders
- [ ] Tenant filter enforced at query layer

---

## Network Analysis

### Example Network Timeline

```
Time  | Request           | Status | Response
------|-------------------|--------|------------------
0s    | GET /api/orders   | 200    | 25 orders
2s    | POST /api/login   | 200    | token + role
5s    | GET /api/orders   | 200    | filtered orders
8s    | GET /api/orders/stream | 200 | SSE established
11s   | [SSE event]       | -      | OrderCreated
14s   | [SSE event]       | -      | OrderStatusUpdated
17s   | PATCH /api/orders/{id}/status | 200 | status updated
20s   | [SSE event]       | -      | OrderStatusUpdated
```

---

## Troubleshooting

### Issue: 401 Unauthorized on All Requests

**Cause**: MSW not intercepting requests
**Solution**:
```bash
# Check if MSW worker started
# DevTools → Console should show:
# [MSW] Mocking enabled

# If not, reload page and check:
npm run dev
# Then navigate to http://localhost:5173
```

### Issue: Orders Not Loading

**Cause**: Mock handler not matching request
**Solution**:
```bash
# Check DevTools → Network tab
# Look for /api/orders request
# Should show 200 status

# If showing 404:
# MSW might not be started, reload page

# If showing 401:
# Add Authorization header, check login
```

### Issue: SSE Connection Fails

**Cause**: Bearer token not sent with EventSource
**Solution**:
```bash
# EventSource API doesn't support custom headers
# Use fetch-based SSE instead (see useSSE hook)
# Or add token to URL: /api/orders/stream?token=xxx
```

### Issue: Pagination Not Working

**Cause**: pageSize > 100 or page < 1
**Solution**:
```bash
# Valid range: page >= 1, pageSize 1-100
# Example: /api/orders?page=1&pageSize=25

# Check URL in DevTools:
# Should show valid pagination params
```

### Issue: Filter Not Filtering

**Cause**: Status value doesn't match exact case
**Solution**:
```bash
# Valid statuses: CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED
# Invalid: Created, in-progress, ready (wrong case/format)

# Check mock handler for case sensitivity
# Status filter is case-sensitive
```

---

## Performance Testing

### Load Test: List Orders
```bash
# In DevTools Console:
for (let i = 0; i < 100; i++) {
  fetch('/api/orders?page=' + i)
}

# Expected: All requests complete in < 100ms
# Mock returns instantly, no server delay
```

### Real-Time Test: SSE Events
```bash
# Monitor EventSource connection in DevTools
# Should receive events every 3 seconds
# No lag or delays

# Calculate latency:
# Event timestamp vs received time
# Should be < 10ms (no transport delay in mock)
```

### Pagination Test: Large Result Sets
```bash
# Test with 1000+ mock orders
# Pagination should still be instant
# Memory usage stable
```

---

## Switching to Real Backend

### Before Switching:
1. ✅ Verify all mock tests pass
2. ✅ Backend server running on http://localhost:8080
3. ✅ Real database populated with test data
4. ✅ CORS headers configured
5. ✅ Authentication endpoint working

### Switch Steps:
```bash
# Option 1: Disable MSW
# Comment out in dashboard-ui/src/main.tsx:
// import { worker } from './mocks/browser';
// worker.start();

# Option 2: Environment variable
export VITE_USE_REAL_BACKEND=true
npm run dev

# Now all requests go to http://localhost:8080
```

### Verify Connection:
```bash
# DevTools → Network
# Look for requests to http://localhost:8080
# Should see 200 responses from real backend

# If 401: Check auth header, token expired?
# If 403: Check tenant ID, cross-tenant access?
# If 500: Backend error, check server logs
```

---

## Documentation Reference

For detailed endpoint specifications, see:
- **`BACKEND_ENDPOINTS_INTEGRATION.md`** - Complete API reference
- **`INTEGRATION_IMPLEMENTATION_SUMMARY.md`** - Implementation details
- **`ALIGNMENT_VERIFICATION_REPORT.md`** - Backend alignment verification
- **`dashboard-ui/src/mocks/handlers.ts`** - Mock implementation

---

**Last Updated**: 2024  
**Mock Status**: ✅ Ready for testing  
**Real Backend**: Ready to integrate  
**Questions?**: Refer to main integration documents
