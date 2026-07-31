# Technical Design Document: Backend Endpoints Integration

## Overview

The Backend Endpoints Integration feature extends the ReadyBell dashboard UI from mocked data to full integration with the restaurant-notification-platform backend. This design covers:

1. **API Client Architecture** - Authentication, interceptors, error handling, retry logic
2. **Real-Time Updates Strategy** - SSE with polling fallback, heartbeat, reconnection
3. **Secure Token Storage** - Bearer token lifecycle, refresh, logout
4. **Multi-Tenant UI Pages** - Phase 2 Staff Admin, Phase 3 Platform Admin navigation
5. **State Management** - Order state machine, optimistic updates, conflict resolution
6. **Security & Isolation** - Role-based access control, tenant context validation
7. **Performance Optimization** - Pagination, ETag caching, request deduplication

---

## System Architecture

### High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────┐
│                  Dashboard UI (React)                   │
│                                                          │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐       │
│  │  Order     │  │ Staff      │  │ Platform   │       │
│  │  Management│  │ Admin Page │  │ Admin Page │       │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘       │
│        │                │                │              │
│        └────────────────┴────────────────┘              │
│                        │                                │
│        ┌───────────────▼───────────────┐               │
│        │     API Client Layer          │               │
│        │ (Request/Response Handlers)   │               │
│        ├──────────────────────────────┤               │
│        │ • Authentication & Bearer     │               │
│        │ • Token Management            │               │
│        │ • Request Interceptors        │               │
│        │ • Response Parsers (RFC7807)  │               │
│        │ • Error Handling & Retry      │               │
│        │ • Real-Time Updates (SSE)     │               │
│        │ • Polling Fallback (ETag)     │               │
│        └───────────────┬───────────────┘               │
│                        │                                │
│        ┌───────────────▼───────────────┐               │
│        │   State Management (Zustand)  │               │
│        │ • Auth State                  │               │
│        │ • Orders State                │               │
│        │ • Admin States                │               │
│        │ • Real-Time Subscriptions     │               │
│        └───────────────┬───────────────┘               │
└────────────────────────┼──────────────────────────────┘
                         │
        ┌────────────────▼─────────────────┐
        │      HTTPS Network Layer         │
        │  (Fetch API + EventSource)       │
        └────────────────┬─────────────────┘
                         │
        ┌────────────────▼──────────────────────┐
        │   Restaurant Notification Platform   │
        │              Backend                  │
        │  ┌──────────────────────────────────┤
        │  │ 22 REST Endpoints                │
        │  │ • Order Management               │
        │  │ • Staff Admin (Phase 2)           │
        │  │ • Platform Admin (Phase 3)       │
        │  │ • Customer Sessions (Phase 4)    │
        │  └──────────────────────────────────┤
        └──────────────────────────────────────┘
```

---

## API Client Layer

### Architecture

The API Client is centralized in `dashboard-ui/src/utils/api.ts` and handles:
- **Request Interceptors**: Inject Bearer token, set headers
- **Response Interceptors**: Parse RFC 7807 errors, handle auth failures
- **Retry Logic**: Exponential backoff for 5xx errors
- **Token Management**: Refresh logic, logout cleanup
- **Real-Time Updates**: SSE + polling fallback

### Authentication Flow

```
┌─────────────┐
│   Login     │
│  Form       │
└──────┬──────┘
       │ (username, password)
       ▼
┌─────────────────────────────────────┐
│ POST /api/auth/login                │
│ (Backend validates credentials)     │
└──────┬──────────────────────────────┘
       │ (201 Created + token)
       ▼
┌──────────────────────────────────────────┐
│ API Client: storeToken(token)            │
│ • Decode JWT: extract tenant_id, role   │
│ • Validate: check expiry + role claims  │
│ • Store: localStorage('auth-token')     │
│ • Set: globalAxios default header       │
└──────┬───────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────┐
│ Redirect: /dashboard or /admin         │
│ (Role-based navigation)                │
└────────────────────────────────────────┘

On 401 Unauthorized:
┌────────────────────────────────────────┐
│ API Client detects 401 response        │
└──────┬─────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────┐
│ Refresh Token? (if refresh token available)   │
│ • POST /api/auth/refresh                       │
│ • Success: retry original request              │
│ • Failure: redirect to /login                  │
└────────────────────────────────────────────────┘
```

### Request/Response Interceptor Pattern

```typescript
// Request Interceptor
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth-token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['Content-Type'] = 'application/json';
  return config;
});

// Response Interceptor
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // Handle 401: Token expired or invalid
    if (error.response?.status === 401) {
      localStorage.removeItem('auth-token');
      window.location.href = '/login';
      return Promise.reject(error);
    }
    
    // Handle 5xx: Retry with exponential backoff
    if (error.response?.status >= 500) {
      return retryWithBackoff(error.config);
    }
    
    // Parse RFC 7807 error
    const problemDetail = parseProblemDetail(error.response?.data);
    return Promise.reject(problemDetail);
  }
);
```

### Error Handling: RFC 7807 Problem Details

All backend errors return Problem Details format:

```typescript
interface ProblemDetail {
  type: string;        // "urn:readybell:{context}:{error}"
  title: string;       // Human-readable title
  status: number;      // HTTP status code
  detail: string;      // Specific error description
  instance?: string;   // Request URI
}

// Common Error Types
type ErrorContext = 
  | 'auth:invalid-token'        // 401
  | 'auth:tenant-mismatch'      // 403
  | 'validation:invalid-request'// 400
  | 'orders:not-found'          // 404
  | 'orders:invalid-transition' // 400
  | 'orders:concurrent-modification' // 409
  | 'admin:staff-not-found'     // 404
  | 'session:invalid-qr'        // 400
```

### Retry Logic: Exponential Backoff

```typescript
async function retryWithBackoff(config, maxRetries = 3) {
  const delays = [1000, 2000, 4000]; // 1s, 2s, 4s
  
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      return await api.request(config);
    } catch (error) {
      if (attempt === maxRetries - 1) throw error;
      if (error.response?.status < 500) throw error; // Don't retry 4xx
      
      await new Promise(r => 
        setTimeout(r, delays[attempt])
      );
    }
  }
}
```

---

## Real-Time Updates Architecture

### SSE Connection Lifecycle

```
┌─────────────────────────────────────────────────────────┐
│ User navigates to Orders page                           │
└──────┬──────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│ useSSE hook establishes connection                      │
│ GET /api/orders/stream                                  │
│ Authorization: Bearer {token}                           │
└──────┬──────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────┐
│ SSE Stream Active (Connection Established)       │
│ Events received: OrderCreated, OrderStatusUpdate │
│ Handler: updateOrderInList()                     │
└──────┬───────────────────────────────────────────┘
       │
       ├─ Every 30s: Heartbeat (:ping event)
       │  └─ Confirms connection alive
       │
       ├─ On OrderStatusUpdated event
       │  └─ Update order in local state
       │  └─ Render order row with new status
       │
       └─ On connection close/error
          │
          ▼
    ┌──────────────────────────────────────┐
    │ Connection failed or closed          │
    │ Attempt reconnect (exponential delay)│
    │ If fails: fallback to polling        │
    └──────────────────────────────────────┘
```

### SSE + Polling Fallback Strategy

```typescript
// Primary: SSE for live updates (< 2 second latency)
if (browser.supportsSSE && token) {
  const eventSource = new EventSource(
    '/api/orders/stream',
    { headers: { Authorization: `Bearer ${token}` } }
  );
  
  eventSource.addEventListener('OrderStatusUpdated', (event) => {
    const order = JSON.parse(event.data);
    updateOrder(order); // Real-time update
  });
}

// Fallback: Polling every 10 seconds with ETag
// (for browsers without SSE support or when SSE fails)
async function pollOrders() {
  const eTag = getLastETag();
  const response = await fetch(
    `/api/orders/poll?since=${lastSyncTime}&eTag=${eTag}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  
  if (response.status === 304) {
    // No changes (data unchanged)
    return;
  }
  
  const { orders, eTag: newETag } = await response.json();
  storeETag(newETag);
  orders.forEach(order => updateOrder(order));
}

// Poll every 10 seconds if SSE unavailable
setInterval(pollOrders, 10000);
```

### Heartbeat & Reconnection

```typescript
// Heartbeat: Detect dead connections
// Backend sends ":ping" every 30 seconds
// If no heartbeat for 60 seconds, consider connection dead

const heartbeatTimeout = setTimeout(() => {
  console.warn('SSE heartbeat timeout, reconnecting...');
  eventSource.close();
  reconnectSSE();
}, 60000);

eventSource.addEventListener('ping', () => {
  clearTimeout(heartbeatTimeout);
  heartbeatTimeout = setTimeout(() => {
    // Timeout logic
  }, 60000);
});

// Reconnection: Exponential backoff
async function reconnectSSE(attempt = 0) {
  const delay = Math.min(1000 * Math.pow(2, attempt), 30000);
  await new Promise(r => setTimeout(r, delay));
  
  try {
    const eventSource = new EventSource(
      '/api/orders/stream',
      { headers: { Authorization: `Bearer ${token}` } }
    );
    // Sync full order list to recover missed events
    await syncOrderList();
  } catch (error) {
    reconnectSSE(attempt + 1);
  }
}
```

### Event Deduplication

When reconnecting after SSE failure, we may receive duplicate events. Deduplication strategy:

```typescript
// Track received events by (orderId, eventType, updatedAt)
const receivedEvents = new Set<string>();

function deduplicateEvent(event: OrderEvent): boolean {
  const key = `${event.orderId}:${event.type}:${event.updatedAt}`;
  if (receivedEvents.has(key)) {
    return false; // Duplicate
  }
  receivedEvents.add(key);
  return true; // New event
}

// Clean up old entries (older than 1 hour)
setInterval(() => {
  const oneHourAgo = Date.now() - 3600000;
  receivedEvents.forEach(key => {
    const [, , timestamp] = key.split(':');
    if (parseInt(timestamp) < oneHourAgo) {
      receivedEvents.delete(key);
    }
  });
}, 300000); // Every 5 minutes
```

---

## Token Storage & Lifecycle

### Bearer Token Storage

**Preference Order** (Security → Practicality):

1. **HttpOnly Cookies** (Most Secure)
   - Backend sets: `Set-Cookie: auth-token=...; HttpOnly; Secure; SameSite=Strict`
   - Frontend cannot access via JavaScript
   - XSS-safe by design
   - Automatic transmission with requests (if SameSite allows)

2. **SessionStorage** (Session-Only)
   - Cleared when browser tab/window closes
   - Not persistent across page reloads
   - Safer than localStorage (session-scoped)

3. **LocalStorage** (Current Implementation)
   - Persistent across page reloads
   - Accessible to XSS attacks
   - Only use with strict Content-Security-Policy

```typescript
// Token Storage Implementation
class TokenManager {
  storeToken(token: string) {
    try {
      // Try httpOnly cookie (backend must support)
      // If not available, fallback to sessionStorage
      if (this.canUseHttpOnlyCookie()) {
        // Backend handles via Set-Cookie
        return;
      }
      
      // Fallback: sessionStorage (cleared on browser close)
      sessionStorage.setItem('auth-token', token);
    } catch (error) {
      console.error('Failed to store token', error);
    }
  }
  
  getToken(): string | null {
    // Check httpOnly cookie first (automatic via fetch)
    // If not found, check sessionStorage
    return sessionStorage.getItem('auth-token');
  }
  
  clearToken() {
    // Clear sessionStorage
    sessionStorage.removeItem('auth-token');
    
    // Clear httpOnly cookie (via logout endpoint)
    fetch('/api/auth/logout', { method: 'POST' });
  }
}
```

### Token Lifecycle

```
┌─────────────────┐
│  User Login     │ ─ POST /api/auth/login ─►
│                 │                           ┌─────────────────────┐
└─────────────────┘                           │ Token Issued        │
                                              │ exp: +1 hour        │
                                              │ role, tenant_id     │
                                              │ refresh: +7 days    │
                                              └──────┬──────────────┘
                                                     │
        ┌────────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ Token Stored in SessionStorage        │
│ (or httpOnly cookie)                  │
└───────────────┬───────────────────────┘
                │
                ├─ Idle: Keep making requests
                │
                ├─ Approaching Expiry (55 min)
                │  └─ Optional: Show "Renew Session" button
                │
                └─ At Expiry (60 min)
                   │
                   ├─ Next Request: 401 Unauthorized
                   │
                   ▼
            ┌──────────────────────────────────┐
            │ Auto Refresh (if refresh token   │
            │ available)                        │
            │ POST /api/auth/refresh            │
            │ + Refresh Token                  │
            └──────┬───────────────────────────┘
                   │
                   ├─ Success (200 OK)
                   │  └─ New token issued
                   │  └─ Retry original request
                   │  └─ Continue session
                   │
                   └─ Failure (401)
                      └─ Redirect /login
                      └─ Clear tokens
                      └─ Session expired message
```

### Token Validation

```typescript
interface DecodedToken {
  sub: string;           // User ID
  tenant_id: string;     // Restaurant tenant
  role: 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN';
  iat: number;           // Issued at
  exp: number;           // Expiration (Unix timestamp)
}

function validateToken(token: string): DecodedToken | null {
  try {
    const payload = JSON.parse(
      atob(token.split('.')[1]) // Decode JWT payload
    );
    
    // Check expiry
    if (payload.exp * 1000 < Date.now()) {
      console.warn('Token expired');
      return null;
    }
    
    // Validate claims
    if (!payload.tenant_id || !payload.role) {
      console.error('Invalid token claims');
      return null;
    }
    
    return payload;
  } catch (error) {
    console.error('Failed to decode token', error);
    return null;
  }
}
```

---

## Multi-Tenant Data Isolation

### Tenant Context Validation

```typescript
interface TenantContext {
  tenant_id: string;
  role: 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN';
}

// Extract tenant context from token
function getTenantContext(): TenantContext | null {
  const token = getToken();
  if (!token) return null;
  
  const payload = validateToken(token);
  return payload ? {
    tenant_id: payload.tenant_id,
    role: payload.role
  } : null;
}

// Validate all API requests include tenant context
async function requestWithTenantValidation(
  endpoint: string,
  options: RequestOptions
) {
  const context = getTenantContext();
  if (!context) {
    throw new Error('Missing tenant context. Please login.');
  }
  
  // Tenant_id extracted from token (no query param needed)
  // Backend enforces: all returned data filters by tenant_id
  return fetch(endpoint, options);
}

// Block cross-tenant access at client side
function canAccessTenant(targetTenantId: string): boolean {
  const context = getTenantContext();
  
  // PLATFORM_ADMIN can access all tenants
  if (context?.role === 'PLATFORM_ADMIN') {
    return true;
  }
  
  // Others can only access their own tenant
  return context?.tenant_id === targetTenantId;
}
```

---

## UI Page Structures

### Phase 1: Enhanced Order Management Dashboard

```
┌─────────────────────────────────────────────────────────┐
│  ReadyBell Dashboard > Orders                           │
│  ───────────────────────────────────────────────────────│
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ Metrics Grid (Real-Time Updates via SSE)            ││
│  │ ┌────────────┐ ┌────────────┐ ┌────────────┐       ││
│  │ │ Incoming   │ │ In Progress│ │ Ready for  │       ││
│  │ │ 5 orders   │ │ 3 orders   │ │ Pickup: 2  │       ││
│  │ └────────────┘ └────────────┘ └────────────┘       ││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ Filter & Sort Controls                              ││
│  │ [Status ▼] [Search...] [CreatedAt ▼] [Asc/Desc]  ││
│  │ Show entries: [25 ▼] | Page: [1 of 3]             ││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ Incoming Orders Table (Updated via SSE)             ││
│  │ ──────────────────────────────────────────────────── ││
│  │ Order ID | Customer | Status      | ETA   | Action  ││
│  │ ──────────────────────────────────────────────────── ││
│  │ #12345   | John     | IN_PROGRESS | 12:30 | [Ready] ││
│  │          |          |             |       | [Cancel]││
│  │ ──────────────────────────────────────────────────── ││
│  │ #12344   | Sarah    | READY       | 12:25 | [Comp.] ││
│  │ (Visual  |          | 🔔 Blink   |       | [Cancel]││
│  │  highlight)          |             |       |         ││
│  │ ──────────────────────────────────────────────────── ││
│  │ #12343   | Mike     | CREATED     | 12:28 | [Start] ││
│  │          |          |             |       | [Cancel]││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
│  Authentication: ✓ Logged in as Staff (Restaurant A)  │
│  Real-time: ✓ Connected (SSE) | Last update: now      │
└─────────────────────────────────────────────────────────┘
```

### Phase 2: Staff Admin Page

```
┌─────────────────────────────────────────────────────────┐
│  ReadyBell Dashboard > Staff Admin                      │
│  ───────────────────────────────────────────────────────│
│                                                          │
│  Breadcrumb: Dashboard > Staff Admin                    │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ STAFF ACCOUNTS                                      ││
│  │ ─────────────────────────────────────────────────── ││
│  │ [+ Create Account]                                  ││
│  │                                                      ││
│  │ Username | Email | Name   | Role  | Status | Action ││
│  │ ────────────────────────────────────────────────── ││
│  │ jsmith   | jsmith| John S.| STAFF | ACTIVE| [Delete]││
│  │ mwood    | mwood | Mary W.| ADMIN | ACTIVE| [Delete]││
│  │ klee     | klee  | Kevin  | STAFF | ACTIVE| [Delete]││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ RESTAURANT SETTINGS                                 ││
│  │ ─────────────────────────────────────────────────── ││
│  │ Restaurant Name: [ Italian Delights      ]          ││
│  │ Phone Number:    [ +1-555-0123           ]          ││
│  │ Address:         [ 123 Main St, City     ]          ││
│  │ Cuisine Type:    [ Italian               ]          ││
│  │ Avg Prep Time:   [ 30 ] minutes                     ││
│  │ Webhook URL:     [ https://example.com/ ]          ││
│  │                                                      ││
│  │ [Save Changes] [Cancel]                            ││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
│  Authentication: ✓ Logged in as Admin (Restaurant A)  │
└─────────────────────────────────────────────────────────┘
```

### Phase 3: Platform Admin Page

```
┌─────────────────────────────────────────────────────────┐
│  ReadyBell Dashboard > Platform Admin                   │
│  ───────────────────────────────────────────────────────│
│                                                          │
│  Breadcrumb: Dashboard > Platform Admin > Restaurants  │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ MANAGE RESTAURANTS                                  ││
│  │ ─────────────────────────────────────────────────── ││
│  │ [+ Create Restaurant] [Refresh]                     ││
│  │                                                      ││
│  │ Tenant ID | Name       | Status   | Created    | Ac ││
│  │ ─────────────────────────────────────────────────── ││
│  │ uuid-001  | Italian    | ACTIVE   | 2024-01-15| [E ││
│  │           | Delights   |          |           | [D ││
│  │ ─────────────────────────────────────────────────── ││
│  │ uuid-002  | Sushi Bar  | ACTIVE   | 2024-01-20| [E ││
│  │           |            |          |           | [D ││
│  │ ─────────────────────────────────────────────────── ││
│  │ uuid-003  | Burger     | INACTIVE | 2024-02-01| [A ││
│  │           | King       |          |           | [D ││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
│  Authentication: ✓ Logged in as Platform Admin        │
└─────────────────────────────────────────────────────────┘
```

### Navigation & Role-Based Access Control

```typescript
interface NavItem {
  label: string;
  href: string;
  icon: string;
  allowedRoles: UserRole[];
  children?: NavItem[];
}

const navigationItems: NavItem[] = [
  {
    label: 'Dashboard',
    href: '/dashboard',
    icon: 'home',
    allowedRoles: ['STAFF', 'ADMIN', 'PLATFORM_ADMIN']
  },
  {
    label: 'Orders',
    href: '/dashboard/orders',
    icon: 'receipt',
    allowedRoles: ['STAFF', 'ADMIN', 'PLATFORM_ADMIN']
  },
  {
    label: 'Staff Admin',
    href: '/admin/staff',
    icon: 'users',
    allowedRoles: ['ADMIN'] // Restaurant Admin only
  },
  {
    label: 'Platform Admin',
    href: '/admin/platform',
    icon: 'settings',
    allowedRoles: ['PLATFORM_ADMIN'] // Platform Admin only
  }
];

function canNavigateTo(item: NavItem, userRole: UserRole): boolean {
  return item.allowedRoles.includes(userRole);
}

// In Layout component:
function renderNavigation(userRole: UserRole) {
  return navigationItems
    .filter(item => canNavigateTo(item, userRole))
    .map(item => (
      <NavLink key={item.href} to={item.href}>
        <Icon name={item.icon} /> {item.label}
      </NavLink>
    ));
}
```

---

## Data Models & State Management

### Order State Machine


```
CREATED ─────────► IN_PROGRESS ─────────► READY ─────────► COMPLETED
  │ └──────────────┘ │ └──────────────┘ │ └──────────────┘
  │ (user can       │ (user can       │ (user can
  │  cancel)        │  cancel)        │  cancel)
  └─────────────────┴─────────────────┴─────────────────────────────
    CANCELLED (terminal state - no further transitions)
```

### Order Data Model

```typescript
interface Order {
  id: string;                    // UUID
  orderId: string;               // User-facing order number (e.g., "ORD-12345")
  tenantId: string;              // Restaurant tenant
  customerId: string;            // Customer identifier
  customerSessionId: string;     // Session reference
  customerName: string;          // Display name
  status: OrderStatus;           // CREATED | IN_PROGRESS | READY | COMPLETED | CANCELLED
  items: OrderItem[];            // Ordered items
  createdAt: ISO8601;            // Creation timestamp
  updatedAt: ISO8601;            // Last modification
  estimatedReadyTime: ISO8601;   // ETA based on prep time
  version: number;               // Optimistic lock version (for conflict detection)
}

interface OrderItem {
  menuItemId: string;
  name: string;
  quantity: number;
  specialInstructions?: string;
}

type OrderStatus = 
  | 'CREATED'
  | 'IN_PROGRESS'
  | 'READY'
  | 'COMPLETED'
  | 'CANCELLED';

// Valid transitions (enforced client-side for UX, server-side for data consistency)
const VALID_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  'CREATED': ['IN_PROGRESS', 'CANCELLED'],
  'IN_PROGRESS': ['READY', 'CANCELLED'],
  'READY': ['COMPLETED', 'CANCELLED'],
  'COMPLETED': [],
  'CANCELLED': []
};

function canTransitionTo(
  currentStatus: OrderStatus,
  targetStatus: OrderStatus
): boolean {
  return VALID_TRANSITIONS[currentStatus]?.includes(targetStatus) ?? false;
}
```

### Staff Account Model

```typescript
interface StaffAccount {
  id: string;                 // UUID
  tenantId: string;           // Restaurant tenant
  username: string;           // Unique login identifier
  email: string;              // Email address
  name: string;               // Display name
  role: 'STAFF' | 'ADMIN';    // Permission level
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: ISO8601;
  deactivatedAt?: ISO8601;
}
```

### Restaurant Tenant Model

```typescript
interface RestaurantTenant {
  tenantId: string;           // UUID
  restaurantName: string;
  ownerEmail: string;
  ownerName: string;
  status: 'ACTIVE' | 'INACTIVE';
  phoneNumber?: string;
  address?: string;
  cuisineType?: string;
  averagePrepTime?: number;   // In seconds
  webhookUrl?: string;
  createdAt: ISO8601;
  updatedAt: ISO8601;
}
```

### Zustand State Management

```typescript
interface AuthState {
  token: string | null;
  refreshToken?: string;
  user: {
    id: string;
    tenant_id: string;
    role: 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN';
  } | null;
  isAuthenticated: boolean;
  login: (token: string, refreshToken?: string) => void;
  logout: () => void;
  setToken: (token: string) => void;
}

interface OrdersState {
  orders: Order[];
  totalCount: number;
  currentPage: number;
  pageSize: number;
  filters: {
    status?: OrderStatus[];
    search?: string;
    sortBy: 'id' | 'customerName' | 'eta' | 'status' | 'created';
    sortOrder: 'asc' | 'desc';
  };
  
  setOrders: (orders: Order[]) => void;
  updateOrder: (order: Order) => void;
  setFilters: (filters: Partial<OrdersState['filters']>) => void;
  setPage: (page: number) => void;
}

interface RealtimeState {
  isSSEConnected: boolean;
  isPolling: boolean;
  lastUpdateTime: number;
  missedEvents: OrderEvent[];
  
  connectSSE: () => void;
  disconnectSSE: () => void;
  recordEvent: (event: OrderEvent) => void;
}

// Create stores
export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  isAuthenticated: false,
  login: (token, refreshToken) => {
    const user = decodeToken(token);
    localStorage.setItem('auth-token', token);
    if (refreshToken) localStorage.setItem('refresh-token', refreshToken);
    set({ token, user, isAuthenticated: true });
  },
  logout: () => {
    localStorage.removeItem('auth-token');
    localStorage.removeItem('refresh-token');
    set({ token: null, user: null, isAuthenticated: false });
  },
  setToken: (token) => set({ token })
}));

export const useOrdersStore = create<OrdersState>((set) => ({
  orders: [],
  totalCount: 0,
  currentPage: 1,
  pageSize: 25,
  filters: { sortBy: 'created', sortOrder: 'desc' },
  
  setOrders: (orders) => set({ orders }),
  updateOrder: (order) =>
    set((state) => ({
      orders: state.orders.map((o) =>
        o.id === order.id ? order : o
      )
    })),
  setFilters: (filters) =>
    set((state) => ({
      filters: { ...state.filters, ...filters }
    })),
  setPage: (page) => set({ currentPage: page })
}));
```

### Optimistic Updates for Order Status

```typescript
async function updateOrderStatus(
  orderId: string,
  newStatus: OrderStatus
) {
  const store = useOrdersStore.getState();
  const order = store.orders.find(o => o.id === orderId);
  
  if (!order) {
    throw new Error('Order not found');
  }
  
  // Validate transition
  if (!canTransitionTo(order.status, newStatus)) {
    throw new Error(`Invalid transition: ${order.status} → ${newStatus}`);
  }
  
  // 1. Optimistic update (immediate UI feedback)
  const previousStatus = order.status;
  store.updateOrder({
    ...order,
    status: newStatus,
    updatedAt: new Date().toISOString()
  });
  
  try {
    // 2. Send to backend
    const response = await api.patch(
      `/api/orders/${orderId}/status`,
      { status: newStatus }
    );
    
    // 3. Confirm update matches server state
    store.updateOrder(response.data);
    
    return response.data;
  } catch (error) {
    // 4. On error, revert optimistic update
    store.updateOrder({
      ...order,
      status: previousStatus
    });
    
    // Handle conflict (409)
    if (error.response?.status === 409) {
      const conflictData = error.response.data;
      showConflictDialog({
        message: 'This order was updated by someone else',
        currentStatus: conflictData.status,
        onRetry: () => updateOrderStatus(orderId, newStatus),
        onRefresh: () => syncOrderState()
      });
    } else {
      showErrorToast(error.message);
    }
    
    throw error;
  }
}
```

---

## Error Handling Design

### 401 Unauthorized: Invalid/Expired Token

```
Backend returns 401 with:
{
  "type": "urn:readybell:auth:invalid-token",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid or expired token"
}

Frontend handling:
1. Clear stored token
2. Redirect to /login
3. Display message: "Your session has expired. Please log in again."
4. Optional: Offer "Continue" button to return to login
```

### 403 Forbidden: Tenant Mismatch or Insufficient Role

```
Backend returns 403 with:
{
  "type": "urn:readybell:auth:tenant-mismatch",
  "title": "Forbidden",
  "status": 403,
  "detail": "You do not have permission to access this restaurant's data"
}

Frontend handling:
1. Do NOT send request if tenant mismatch detected client-side
2. If received from backend, display error toast
3. Remove the data item from UI
4. Log warning to console
```

### 404 Not Found: Resource Deleted

```
Backend returns 404 with:
{
  "type": "urn:readybell:orders:not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Order #12345 was not found"
}

Frontend handling:
1. Show toast: "Order not found"
2. Remove order from table
3. If order was being edited, close the dialog
```

### 409 Conflict: Concurrent Modification

```
Backend returns 409 with:
{
  "type": "urn:readybell:orders:concurrent-modification",
  "title": "Conflict",
  "status": 409,
  "detail": "Order was updated concurrently",
  "instance": "/api/orders/uuid-123"
}

Response body includes current order state:
{
  "id": "uuid-123",
  "status": "READY",
  "version": 3,
  "updatedAt": "2024-01-15T14:35:22Z"
}

Frontend handling:
1. Revert optimistic update
2. Show conflict resolution dialog:
   - "This order was updated by someone else"
   - Show current status from server
   - Options: [Retry] [Refresh] [Cancel]
3. If Retry: Send update with latest version
4. If Refresh: Load order state from backend
```

### 5xx Server Error: Retry with Backoff

```
Backend returns 500+ error

Frontend handling:
1. Show error toast: "Service unavailable. Retrying..."
2. Retry with exponential backoff (1s, 2s, 4s)
3. Max 3 retries
4. If all retries fail: Show "Contact support" message
5. Offer manual retry button
```

---

## Testing Strategy

### Unit Tests: API Client

- Token storage/retrieval/clearing
- Request interceptor: Bearer token injection
- Response interceptor: Error parsing (RFC 7807)
- Retry logic: Exponential backoff, max attempts
- Token validation: Decode JWT, check expiry
- Tenant context extraction: Parse role claims

### Unit Tests: State Management

- Order state machine: Valid transitions only
- Optimistic updates: Revert on error
- Conflict detection: Version mismatch
- Filter/sort: Preserve URL parameters
- Token lifecycle: Login/refresh/logout

### Unit Tests: UI Components

- Order table: Render rows, handle pagination
- Status buttons: Show allowed transitions
- Filter controls: Apply filters to API request
- Admin forms: Validation, error display
- Navigation: Role-based visibility

### Integration Tests: Real-Time Updates

- SSE connection: Receive OrderCreated event
- SSE reconnection: Exponential backoff
- Polling fallback: ETag support, 304 responses
- Event deduplication: Ignore duplicates
- Conflict handling: Show dialog, retry

### Integration Tests: Multi-Tenant Isolation

- Cross-tenant access: 403 Forbidden
- Tenant filter: Only own restaurant data
- Role-based access: STAFF cannot access Admin pages
- Navigation: Role-based links visible

### E2E Tests: Complete Workflows

- Login → View Orders → Update Status → Logout
- Staff Admin: Create Account → Deactivate
- Platform Admin: Create Restaurant → Edit → Deactivate
- Real-time: Order updates visible within 2 seconds
- Conflict: Update order, receive 409, retry succeeds

### Performance Tests

- Orders list: Load < 1 second (25 items)
- Filter/sort: Response < 1 second (10,000 items)
- Real-time latency: SSE update visible < 2 seconds
- ETag efficiency: Polling with 304 < 100ms

---

## Security Considerations

### Bearer Token Security

✅ **DO**:
- Store in httpOnly cookie (XSS-safe)
- Transmit over HTTPS only
- Include in Authorization header
- Clear on logout
- Validate expiry before use
- Use SameSite=Strict for cookies

❌ **DON'T**:
- Store in localStorage (XSS-vulnerable)
- Include in URL query parameters
- Include in request body (except during refresh)
- Log tokens in console
- Store with window.token

### CORS & CSP

- Backend CORS headers: Allow frontend domain only
- Content-Security-Policy: Strict CSP headers
- No inline scripts
- Only HTTPS connections

### Input Validation

- Client-side: Validate before sending (UX feedback)
- Server-side: Always validate (security requirement)
- Sanitize: Filter status values, pagination params
- UTF-8: Handle special characters safely

### Authorization Checks

- Validate Bearer token before accessing admin pages
- Check role claims: STAFF/ADMIN/PLATFORM_ADMIN
- Block cross-tenant access client-side
- Server-side: Always filter by tenant_id

---

## Performance Optimization

### Request Deduplication

```typescript
// Track in-flight requests to avoid duplicates
const inFlightRequests = new Map<string, Promise<any>>();

async function deduplicatedFetch(url: string, options: RequestOptions) {
  const key = `${options.method || 'GET'} ${url}`;
  
  if (inFlightRequests.has(key)) {
    return inFlightRequests.get(key);
  }
  
  const promise = api.request({ url, ...options })
    .finally(() => inFlightRequests.delete(key));
  
  inFlightRequests.set(key, promise);
  return promise;
}
```

### Pagination (Default 25 Items)

```
GET /api/orders?page=1&pageSize=25

Response includes:
{
  "items": [...],      // 25 items
  "totalCount": 500,   // Total items in dataset
  "pageCount": 20,     // Number of pages
  "currentPage": 1
}

Client: Display "Page 1 of 20"
```

### ETag-Based Conditional Requests

```
// First request
GET /api/orders/poll
Response 200:
{
  "orders": [...],
  "eTag": "abc123def456"
}

// Subsequent requests with ETag
GET /api/orders/poll?eTag=abc123def456
Response 304 (Not Modified): Empty body, orders unchanged

// New data available
GET /api/orders/poll?eTag=abc123def456
Response 200:
{
  "orders": [...],  // New orders
  "eTag": "xyz789"
}
```

### Caching Strategy

- **Orders List**: Cache with 10-second TTL
- **Staff Accounts**: Cache with 30-second TTL
- **Restaurant Config**: Cache with 5-minute TTL (or invalidate on update)
- **Real-time updates**: Invalidate cache immediately via SSE

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Bearer Token Lifecycle Completion

**For any** authenticated user session, after login the token SHALL be stored securely and transmitted with all protected API requests until logout, at which point the token SHALL be cleared and no further authenticated requests SHALL be possible without re-authentication.

**Validates: Requirements 1.1, 1.5, 1.6, 16.1, 16.5**

### Property 2: Real-Time Update Delivery within SLA

**For any** order status change on the backend, the Dashboard UI SHALL display the updated status within 2 seconds through either SSE stream delivery or polling fallback, and SHALL maintain heartbeat connectivity to detect dead connections within 5 seconds.

**Validates: Requirements 2.2, 2.5, 2.6, 12.1, 12.3**

### Property 3: Order State Transition Validity

**For any** order in the system, transitions between statuses SHALL only proceed if the transition is valid per the Order_Lifecycle state machine (CREATED→IN_PROGRESS→READY→COMPLETED), and any invalid transition attempt SHALL be rejected with a 400 error on the backend and blocked client-side before sending the request.

**Validates: Requirements 4.1, 4.2**

### Property 4: Conflict Resolution on Concurrent Modification

**For any** order that receives concurrent modification attempts, the first request SHALL succeed, the second request SHALL receive a 409 Conflict response with current order state, and the UI SHALL display a conflict resolution dialog allowing the user to retry or refresh.

**Validates: Requirements 4.3, 4.4, 11.4**

### Property 5: Multi-Tenant Data Isolation

**For any** authenticated user with a specific Tenant_Id in their Bearer Token, all API requests SHALL only return data matching that Tenant_Id, and any attempt to access cross-tenant data SHALL either be blocked client-side (if tenant mismatch is detectable) or return 403 Forbidden from the backend.

**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6**

### Property 6: RFC 7807 Error Response Parsing

**For any** API error response, the Dashboard UI SHALL parse the Problem_Detail structure (type, title, status, detail fields) and display a user-friendly message based on the detail field without exposing internal technical details.

**Validates: Requirements 6.1, 6.2, 6.3**

### Property 7: Role-Based Access Control Enforcement

**For any** user with a specific role (STAFF/ADMIN/PLATFORM_ADMIN), the Dashboard_UI SHALL display navigation links only for pages matching their role, and attempts to manually navigate to unauthorized pages SHALL display "Access Denied" without sending requests to protected endpoints.

**Validates: Requirements 13.1, 13.2, 13.3, 13.4**

### Property 8: Optimistic Update Reversion

**For any** optimistic UI update to order status, if the backend request fails, the UI update SHALL be automatically reverted to the previous status and an error message SHALL be displayed to the user.

**Validates: Requirements 11.2, 11.3, 11.5**

### Property 9: Pagination and Filtering Preservation

**For any** user applying filters or sorting to the orders list, the filter parameters SHALL be persisted in URL query parameters such that bookmarking or sharing the URL preserves the filtered view when reopened.

**Validates: Requirement 3.6**

### Property 10: Secure Token Storage and Transmission

**For any** Bearer Token stored by the Dashboard UI, it SHALL be stored in secure storage (httpOnly cookies or sessionStorage) and SHALL only be transmitted over HTTPS connections in the Authorization header, and SHALL NOT appear in URL query parameters, request body logs, or browser console.

**Validates: Requirements 16.2, 16.4, 16.6**



## Implementation Architecture Summary

### File Structure

```
dashboard-ui/src/
├── api/
│   ├── client.ts          # Axios instance with interceptors
│   ├── endpoints.ts       # API endpoint functions
│   ├── errors.ts          # RFC 7807 error handling
│   └── auth.ts            # Authentication endpoints
├── hooks/
│   ├── useSSE.ts          # Server-Sent Events connection
│   ├── usePolling.ts      # Polling fallback with ETag
│   ├── useOrders.ts       # Orders data fetching
│   └── useAuth.ts         # Authentication state
├── store/
│   ├── auth.ts            # Auth state (Zustand)
│   ├── orders.ts          # Orders state (Zustand)
│   └── realtime.ts        # Real-time connection state
├── components/
│   ├── OrdersTable.tsx    # Order list component
│   ├── StatusButtons.tsx  # Status transition buttons
│   ├── AdminPanel.tsx     # Phase 2 & 3 admin pages
│   └── ConflictDialog.tsx # Conflict resolution
├── types/
│   ├── api.ts             # API response types
│   ├── orders.ts          # Order domain types
│   ├── admin.ts           # Admin domain types
│   └── errors.ts          # Error types
├── utils/
│   ├── token.ts           # Token encode/decode/validate
│   ├── navigation.ts      # Role-based nav
│   └── retry.ts           # Retry logic
└── pages/
    ├── Dashboard.tsx      # Phase 1: Orders
    ├── StaffAdmin.tsx     # Phase 2: Admin pages
    └── PlatformAdmin.tsx  # Phase 3: Platform admin
```

### API Client Implementation

```typescript
// api/client.ts
import axios from 'axios';
import { handleAuthError, parseProblemDetail } from './errors';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
});

// Request interceptor: Inject Bearer token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth-token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['Content-Type'] = 'application/json';
  return config;
});

// Response interceptor: Handle errors and retries
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { status, data } = error.response || {};
    
    // Handle 401
    if (status === 401) {
      handleAuthError();
      return Promise.reject(error);
    }
    
    // Handle 5xx with retry
    if (status >= 500) {
      return retryWithBackoff(error.config);
    }
    
    // Parse RFC 7807 error
    const problemDetail = parseProblemDetail(data);
    return Promise.reject(problemDetail);
  }
);

export default api;
```

### Real-Time Connection Management

```typescript
// hooks/useSSE.ts
import { useEffect, useRef } from 'react';
import { useOrdersStore } from '@/store/orders';
import { useAuthStore } from '@/store/auth';

export function useSSE() {
  const { orders, updateOrder } = useOrdersStore();
  const { token } = useAuthStore();
  const eventSourceRef = useRef<EventSource | null>(null);
  const heartbeatTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!token) return;

    // Establish SSE connection
    const eventSource = new EventSource('/api/orders/stream', {
      headers: { Authorization: `Bearer ${token}` }
    });

    // Handle OrderCreated events
    eventSource.addEventListener('OrderCreated', (event) => {
      const order = JSON.parse(event.data);
      updateOrder(order);
    });

    // Handle OrderStatusUpdated events
    eventSource.addEventListener('OrderStatusUpdated', (event) => {
      const order = JSON.parse(event.data);
      updateOrder(order);
    });

    // Handle heartbeat
    eventSource.addEventListener('ping', () => {
      clearTimeout(heartbeatTimeoutRef.current);
      heartbeatTimeoutRef.current = setTimeout(() => {
        console.warn('Heartbeat timeout, reconnecting...');
        eventSource.close();
      }, 60000);
    });

    eventSourceRef.current = eventSource;
    return () => {
      eventSource.close();
      clearTimeout(heartbeatTimeoutRef.current);
    };
  }, [token]);
}
```

### State Update Pattern

```typescript
// Example: Update order status with optimistic update
async function handleStatusChange(orderId: string, newStatus: OrderStatus) {
  const store = useOrdersStore.getState();
  const order = store.orders.find(o => o.id === orderId);
  
  if (!order) return;
  if (!canTransitionTo(order.status, newStatus)) {
    showError('Invalid status transition');
    return;
  }

  // Store previous state for rollback
  const previousStatus = order.status;
  
  // 1. Optimistic update
  store.updateOrder({ ...order, status: newStatus });

  try {
    // 2. Send to backend
    const response = await api.patch(`/api/orders/${orderId}/status`, {
      status: newStatus
    });

    // 3. Confirm with server state
    store.updateOrder(response.data);
    showSuccess('Status updated');
  } catch (error) {
    // 4. Rollback on error
    store.updateOrder({ ...order, status: previousStatus });
    
    if (error.status === 409) {
      showConflictDialog(error);
    } else {
      showError(error.message);
    }
  }
}
```

---

## Migration Path from Mocked to Real Backend

### Step 1: Update API Client

1. Replace mock handlers with real endpoints
2. Add Bearer token interceptor
3. Enable all error handling (401, 403, 404, 409, 5xx)

### Step 2: Implement Authentication

1. Create login endpoint integration
2. Implement token storage and retrieval
3. Add token validation on app initialization
4. Add logout functionality

### Step 3: Enable Real-Time Updates

1. Replace mocked SSE with fetch-based EventSource
2. Implement polling fallback with ETag
3. Add heartbeat/reconnection logic

### Step 4: Add Admin Pages (Phase 2 & 3)

1. Create Staff Admin page UI
2. Implement staff account management endpoints
3. Implement restaurant config endpoints
4. Create Platform Admin page UI
5. Implement restaurant tenant endpoints

### Step 5: Implement Multi-Tenant Isolation

1. Extract tenant_id from Bearer Token
2. Validate tenant_id in all API requests
3. Block cross-tenant access client-side
4. Add tenant filter UI

---

## Configuration & Environment Variables

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_REAL_BACKEND=true
VITE_SESSION_TIMEOUT_MINUTES=30

# .env.production
VITE_API_BASE_URL=https://api.readybell.com
VITE_USE_REAL_BACKEND=true
VITE_SESSION_TIMEOUT_MINUTES=30
```

---

## Deployment Considerations

### Frontend Deployment

- Build: `npm run build`
- Deploy to CDN (Vercel, Netlify, or S3 + CloudFront)
- Configure CORS headers for backend domain
- Set Content-Security-Policy headers
- Enable HTTPS only

### Backend Integration

- Verify CORS headers allow frontend domain
- Verify Bearer token validation and expiry
- Test all 22 endpoints with real data
- Load test: 100+ concurrent connections
- Monitor SSE connection reliability

### Monitoring & Observability

- Log API requests/responses (with tokens redacted)
- Track real-time latency metrics
- Alert on 5xx errors
- Monitor token refresh failures
- Track page load performance

---

## Known Limitations & Future Work

### Current Limitations

1. **Refresh Token**: Not implemented in this design (tokens expire after 1 hour)
   - Future: Implement refresh token endpoint and auto-refresh logic
   
2. **Offline Support**: No offline mode for order updates
   - Future: Use IndexedDB + Service Workers for offline capability
   
3. **WebSocket**: SSE used instead of WebSocket
   - Future: Consider WebSocket if real-time requirements increase
   
4. **Batch Operations**: Only single-order updates supported
   - Future: Add bulk status update endpoint

5. **Push Notifications**: Not in Phase 1
   - Future: Integrate with Web Push API (Phase 4)

### Future Enhancements

- [ ] Implement refresh token and auto-refresh
- [ ] Add offline mode with IndexedDB
- [ ] Migrate SSE to WebSocket for lower latency
- [ ] Add batch order update endpoint
- [ ] Implement push notifications
- [ ] Add user preference persistence
- [ ] Implement audit logging for sensitive operations
- [ ] Add analytics/telemetry

---

## References & Related Documents

- **Backend Spec**: `.kiro/specs/restaurant-notification-platform/`
- **Requirements**: `.kiro/specs/backend-endpoints-integration/requirements.md`
- **Backend Contract**: `BACKEND_ENDPOINTS_INTEGRATION.md`
- **API Documentation**: Backend README.md
- **Security**: OWASP Top 10, RFC 7807, RFC 6750 (Bearer Token)

---

**Document Version**: 1.0  
**Status**: Ready for Review  
**Last Updated**: January 2024  
**Next Phase**: Requirements Approval → Design Approval → Task Creation
