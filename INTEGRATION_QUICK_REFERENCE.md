# Integration Quick Reference Card

## Start Dashboard with Mock Backend
```bash
cd dashboard-ui && npm run dev
# Runs on http://localhost:5173
# Mock intercepts all /api/* requests
```

## Test Credentials
```
staff@restaurant.com  / password    → STAFF role, token: staff-tenant-123-abc123
admin@restaurant.com  / password    → ADMIN role, token: admin-tenant-123-def456
```

## API Endpoints Summary

| Method | Endpoint | Auth | Status |
|--------|----------|------|--------|
| POST | `/api/login` | No | ✅ Implemented |
| GET | `/api/orders` | Bearer | ✅ Implemented |
| POST | `/api/orders` | Bearer | ✅ Implemented |
| PATCH | `/api/orders/{id}/status` | Bearer | ✅ Implemented |
| GET | `/api/orders/stream` | Bearer | ✅ Implemented |
| GET | `/api/orders/poll` | Bearer | ✅ Implemented |
| POST | `/api/admin/staff` | Bearer (ADMIN) | 🔷 Stub |
| PUT | `/api/admin/config` | Bearer (ADMIN) | 🔷 Stub |

Legend: ✅ = Fully Mocked | 🔷 = Stub Only

## Error Response Format (RFC 7807)
```json
{
  "type": "urn:readybell:{context}:{error}",
  "title": "Human Readable",
  "status": 400,
  "detail": "Specific description"
}
```

## Order Status Lifecycle
```
CREATED
  ├─→ IN_PROGRESS
  └─→ CANCELLED

IN_PROGRESS
  ├─→ READY
  └─→ CANCELLED

READY
  ├─→ COMPLETED
  └─→ CANCELLED

COMPLETED (terminal)
CANCELLED (terminal)
```

## Bearer Token Usage
```
Authorization: Bearer {token}

Mock Token Format:
- staff-tenant-123-abc123      (STAFF role)
- admin-tenant-123-def456       (ADMIN role)
- platform-xxx-yyy              (PLATFORM_ADMIN)
```

## Query Parameters

### GET /api/orders
```
?page=1                    (default: 1, min: 1)
&pageSize=25               (default: 25, max: 100)
&status=CREATED,READY      (comma-separated, optional)
&sortBy=orderId            (orderId, status, eta, customerName)
&sortOrder=asc             (asc or desc, default: desc)
&search=john               (searches order/customer/items)
```

### GET /api/orders/poll
```
?since=2024-01-15T10:00:00Z   (optional, timestamp)
&eTag=%22abc123%22             (optional, string)
```

## HTTP Status Codes
| Code | Meaning | When |
|------|---------|------|
| 200 | OK | Successful GET/PATCH |
| 201 | Created | Successful POST |
| 304 | Not Modified | Poll request, no change |
| 400 | Bad Request | Invalid params, validation failed |
| 401 | Unauthorized | Missing/invalid token |
| 403 | Forbidden | Cross-tenant access |
| 404 | Not Found | Order doesn't exist |
| 409 | Conflict | Concurrent modification |
| 500 | Server Error | Backend error |

## Test Error Scenarios
```
GET /api/orders/error/network     → Network timeout
GET /api/orders/error/401         → Invalid token
GET /api/orders/error/403         → Tenant mismatch
GET /api/orders/error/500         → Server error
```

## Mock Data

**Orders Generated**: 100 per request  
**Status Distribution**:
- 20% CREATED (new)
- 30% IN_PROGRESS (preparing)
- 20% READY (ready)
- 20% COMPLETED (completed)
- 10% CANCELLED

**Pagination**: Default 25 per page, max 100  
**Tenant Scoping**: All orders belong to authenticated tenant

## Real-Time Streams

### SSE (Server-Sent Events)
```
GET /api/orders/stream
Content-Type: text/event-stream

Events:
- OrderCreated (new order)
- OrderStatusUpdated (status changed)
- OrderReady (ready for pickup)

Rate: ~3 sec interval
```

### Polling Fallback
```
GET /api/orders/poll?eTag="hash"

Response: 
- 200: New orders + ETag
- 304: No change, same ETag
```

## Multi-Tenant

**Isolation**: Enforced at query layer  
**Tenant ID**: Extracted from token  
**Scope**: All orders, subscriptions, staff scoped to tenant  
**Access**: Cannot read/write other tenant's data

## Switch to Real Backend

### Option 1: Disable MSW
```typescript
// dashboard-ui/src/main.tsx
// Comment out these lines:
// import { worker } from './mocks/browser';
// worker.start();
```

### Option 2: Environment Variable
```bash
VITE_USE_REAL_BACKEND=true npm run dev
```

### Prerequisites
```bash
# Terminal 1: Backend
./gradlew bootRun
# Listens on http://localhost:8080

# Terminal 2: Frontend
cd dashboard-ui
VITE_USE_REAL_BACKEND=true npm run dev
# Connects to http://localhost:8080
```

## Testing Checklist

- [ ] Can list orders with mock
- [ ] Can filter by status
- [ ] Can sort by column
- [ ] Can update order status
- [ ] Real-time updates via SSE
- [ ] Polling fallback works
- [ ] Authentication works
- [ ] 401 error for invalid token
- [ ] 403 error for cross-tenant
- [ ] Error messages display correctly
- [ ] All endpoints documented
- [ ] Ready for real backend

## Documentation Files

- **`BACKEND_ENDPOINTS_INTEGRATION.md`** (700+ lines)
  - Complete API reference
  - All 6 endpoint groups with full specs
  - RFC 7807 error format
  - Integration checklist

- **`INTEGRATION_IMPLEMENTATION_SUMMARY.md`** (400+ lines)
  - What was done
  - Current status (Phase 1 complete)
  - Next steps (5 phases planned)
  - Testing instructions

- **`INTEGRATION_TESTING_GUIDE.md`** (300+ lines)
  - Test scenarios with steps
  - Network analysis
  - Troubleshooting guide
  - Performance testing

- **`INTEGRATION_QUICK_REFERENCE.md`** (this file)
  - Quick lookup card
  - Common commands
  - Error codes
  - Checklists

## Key Files

```
dashboard-ui/
├── src/
│   ├── mocks/
│   │   └── handlers.ts          ← Mock API (15+ endpoints)
│   ├── utils/
│   │   └── api.ts               ← API client (Bearer token support)
│   ├── types/
│   │   └── index.ts             ← Type definitions
│   ├── context/
│   │   └── SearchContext.tsx    ← Search state
│   ├── hooks/
│   │   ├── useSSE.ts            ← SSE client
│   │   └── useOrderPolling.ts   ← Polling fallback
│   └── main.tsx                 ← MSW setup
│
└── docs/
    ├── BACKEND_ENDPOINTS_INTEGRATION.md
    ├── INTEGRATION_IMPLEMENTATION_SUMMARY.md
    ├── INTEGRATION_TESTING_GUIDE.md
    └── INTEGRATION_QUICK_REFERENCE.md (this file)
```

## Common Commands

```bash
# Start development
npm run dev

# Run tests
npm run test

# Build production
npm run build

# Lint
npm run lint

# Format
npm run format

# With real backend
VITE_USE_REAL_BACKEND=true npm run dev
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│             Dashboard UI (React + TypeScript)            │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Layout     │  │   Orders     │  │   Filters    │  │
│  │   Components │  │   Table      │  │   Search     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│         │                  │                  │          │
│         └──────────────────┼──────────────────┘          │
│                            │                            │
│         ┌──────────────────────────────────┐            │
│         │     API Client (Bearer Auth)     │            │
│         └──────────────────┬───────────────┘            │
│                            │                            │
└────────────────────────────┼────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
                    ▼                 ▼
            ┌──────────────┐   ┌──────────────┐
            │  MSW Mock    │   │ Real Backend │
            │  (Dev/Test)  │   │  (Production)|
            └──────────────┘   └──────────────┘
```

## Status

| Component | Status | Notes |
|-----------|--------|-------|
| API Client | ✅ Ready | Bearer token support |
| Mock Handlers | ✅ Ready | 15 endpoints, 400 lines |
| Authentication | ✅ Ready | Token validation |
| Order Management | ✅ Ready | Full CRUD + filters |
| Real-Time Updates | ✅ Ready | SSE + polling |
| Error Handling | ✅ Ready | RFC 7807 format |
| Documentation | ✅ Ready | 1500+ lines across 4 docs |
| Staff Admin | 🔷 Stub | Auth & structure ready |
| Platform Admin | 🔷 Stub | Auth & structure ready |
| Real Backend Ready | ✅ | Can switch anytime |

Legend: ✅ = Complete | 🔷 = Partial | ❌ = Not Started

---

**Version**: 1.0  
**Last Updated**: 2024  
**Status**: ✅ Ready for Testing & Real Backend Integration
