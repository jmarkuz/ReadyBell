# useAuth Hook

A comprehensive React hook for managing authentication state and actions in the ReadyBell dashboard.

## Overview

The `useAuth` hook provides reactive access to authentication state and actions, including:

- **Authentication State**: Track if user is logged in, loading status, errors
- **User Information**: Access user profile, ID, username, email, role, tenant ID
- **Auth Actions**: Login, logout, token refresh
- **Token Management**: Monitor token expiry, detect expiring tokens
- **Multi-Tenant Support**: Track tenant context for data isolation

## Basic Usage

### Simple Login/Logout

```tsx
import { useAuth } from '@/hooks';

function LoginPage() {
  const { isAuthenticated, user, login, logout } = useAuth();

  if (isAuthenticated) {
    return (
      <div>
        <h1>Welcome, {user?.name}!</h1>
        <button onClick={logout}>Logout</button>
      </div>
    );
  }

  return (
    <button onClick={() => login({ username: 'john', password: 'pass123' })}>
      Login
    </button>
  );
}
```

### Conditional Rendering

```tsx
import { useAuth } from '@/hooks';

function Dashboard() {
  const { isAuthenticated, loading, error } = useAuth();

  if (loading) return <p>Loading authentication...</p>;
  if (error) return <p>Error: {error}</p>;
  if (!isAuthenticated) return <Redirect to="/login" />;

  return <OrdersPage />;
}
```

### Role-Based Access Control

```tsx
import { useAuth } from '@/hooks';

function AdminPanel() {
  const { role } = useAuth();

  if (role !== 'ADMIN' && role !== 'PLATFORM_ADMIN') {
    return <div>Access Denied</div>;
  }

  return <AdminContent />;
}
```

### Multi-Tenant Context

```tsx
import { useAuth } from '@/hooks';

function RestaurantOrders() {
  const { tenantId, isAuthenticated } = useAuth();

  if (!isAuthenticated) return <LoginRequired />;

  return <OrderList restaurantId={tenantId} />;
}
```

## API Reference

### State

```typescript
interface AuthState {
  // Main authentication state
  isAuthenticated: boolean;    // True if user is logged in
  user: UserProfile | null;    // User profile or null
  loading: boolean;            // True while checking authentication
  error: string | null;        // Error message if any

  // User info shortcuts (derived from user profile)
  userId: string | null;
  username: string | null;
  email: string | null;
  name: string | null;
  role: 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN' | null;
  tenantId: string | null;

  // Token expiry tracking
  isTokenExpiringSoon: boolean; // True if token expires within threshold
  timeUntilExpiry: number;      // Seconds until token expires
}
```

### Actions

```typescript
interface AuthActions {
  // Login with credentials
  login: (credentials: { username: string; password: string }) => Promise<void>;

  // Logout and clear session
  logout: () => void;

  // Refresh authentication token
  refreshToken: () => Promise<boolean>;

  // Clear error message
  clearError: () => void;
}
```

### Options

```typescript
interface UseAuthOptions {
  // Validate token on component mount (default: true)
  validateOnMount?: boolean;

  // Proactively refresh token before expiry (seconds, default: 300 = 5 min)
  // Set to 0 to disable proactive refresh
  autoRefreshThreshold?: number;

  // Polling interval for checking token expiry (ms, default: 60000)
  expiryCheckInterval?: number;
}
```

## Advanced Examples

### Token Expiry Monitoring

```tsx
import { useAuth } from '@/hooks';

function SessionStatus() {
  const { isTokenExpiringSoon, timeUntilExpiry } = useAuth();

  if (!isTokenExpiringSoon) return null;

  return (
    <div className="alert">
      Session expiring in {Math.floor(timeUntilExpiry / 60)} minutes
      <button onClick={() => /* refresh */}>Renew Session</button>
    </div>
  );
}
```

### Proactive Token Refresh

```tsx
import { useAuth } from '@/hooks';

function App() {
  // Automatically refresh token 5 minutes before expiry
  const { isAuthenticated } = useAuth({
    validateOnMount: true,
    autoRefreshThreshold: 300, // 5 minutes
    expiryCheckInterval: 60000, // Check every minute
  });

  if (!isAuthenticated) return <LoginPage />;
  return <MainApp />;
}
```

### Handle Login Errors

```tsx
import { useAuth } from '@/hooks';
import { useState } from 'react';

function LoginForm() {
  const { login, error, clearError } = useAuth();
  const [credentials, setCredentials] = useState({ username: '', password: '' });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await login(credentials);
    } catch (err) {
      // Error is stored in `error` state
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {error && (
        <div className="error">
          {error}
          <button onClick={clearError}>Dismiss</button>
        </div>
      )}
      <input
        value={credentials.username}
        onChange={(e) =>
          setCredentials({ ...credentials, username: e.target.value })
        }
        placeholder="Username"
      />
      <input
        type="password"
        value={credentials.password}
        onChange={(e) =>
          setCredentials({ ...credentials, password: e.target.value })
        }
        placeholder="Password"
      />
      <button type="submit">Login</button>
    </form>
  );
}
```

### Integration with React Router

```tsx
import { useAuth } from '@/hooks';
import { Navigate } from 'react-router-dom';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();

  if (loading) return <div>Loading...</div>;
  if (!isAuthenticated) return <Navigate to="/login" />;

  return <>{children}</>;
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, role, loading } = useAuth();

  if (loading) return <div>Loading...</div>;
  if (!isAuthenticated) return <Navigate to="/login" />;
  if (role !== 'ADMIN' && role !== 'PLATFORM_ADMIN') {
    return <Navigate to="/dashboard" />;
  }

  return <>{children}</>;
}
```

### API Request with Auth Context

```tsx
import { useAuth } from '@/hooks';
import { useEffect, useState } from 'react';

function OrdersList() {
  const { tenantId, isAuthenticated } = useAuth();
  const [orders, setOrders] = useState([]);

  useEffect(() => {
    if (!isAuthenticated || !tenantId) return;

    const fetchOrders = async () => {
      const response = await fetch(`/api/orders?tenantId=${tenantId}`, {
        headers: {
          'Authorization': `Bearer ${/* token from hook or api client */}`,
        },
      });
      const data = await response.json();
      setOrders(data.items);
    };

    fetchOrders();
  }, [isAuthenticated, tenantId]);

  return (
    <div>
      {orders.map((order) => (
        <div key={order.id}>{order.id}</div>
      ))}
    </div>
  );
}
```

## Implementation Details

### Authentication Flow

1. **Initialization**: On component mount, the hook validates the stored token
2. **Token Storage**: Tokens are stored in sessionStorage (default) or localStorage
3. **Token Validation**: Tokens are decoded to extract user claims (no signature verification)
4. **Expiry Monitoring**: The hook periodically checks token expiry
5. **Proactive Refresh**: If enabled, refreshes token before expiry
6. **Error Handling**: 401/403 responses trigger logout or token refresh

### Token Claims

The hook extracts these claims from the JWT token:

```typescript
{
  sub: string;              // User ID
  username: string;         // Username
  email: string;           // Email
  name: string;            // Full name
  role: string;            // 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN'
  tenant_id: string;       // Restaurant tenant ID
  iat: number;             // Issued at (Unix timestamp)
  exp: number;             // Expires at (Unix timestamp)
}
```

### Token Storage

Tokens are stored securely in:

- **Primary**: `sessionStorage` (cleared on browser close)
- **Fallback**: `localStorage` (if sessionStorage unavailable)

This ensures tokens are never stored permanently or accessible to XSS attacks.

### Multi-Tenant Context

The hook automatically extracts tenant context from the token claims:

- `tenantId`: Used to filter API requests to specific restaurant data
- `role`: Used to enforce role-based access control on the UI
- Validation: Prevents accidental cross-tenant access

## Security Considerations

### XSS Prevention

- Tokens are stored in sessionStorage (memory-based, cleared on close)
- No tokens exposed in URLs or console output
- Use Content-Security-Policy headers (backend-managed)

### CSRF Protection

- Bearer tokens in Authorization header (not in cookies)
- Server validates token on every request
- Tokens include tenant context for multi-tenant isolation

### Token Expiry

- Tokens expire after configurable duration (default 1 hour)
- Hook detects expiry and triggers refresh or logout
- Proactive refresh prevents session interruption

## Troubleshooting

### Token Not Being Stored

Check that:
- The API login endpoint returns a `token` field
- Token is a valid JWT (3 parts separated by dots)
- sessionStorage is not disabled in browser
- No Content Security Policy blocking token access

### Hook State Always Empty

Verify:
- `validateOnMount` is true (default)
- Component has mounted (use useEffect to check)
- Mock token manager in tests (see useAuth.test.ts)

### Token Refresh Not Working

Ensure:
- `autoRefreshThreshold` is > 0 (default 300 seconds)
- Token expiry is set in JWT payload
- API endpoint returns new token

## Testing

The hook includes comprehensive tests covering:

- Initial state (unauthenticated, no token)
- Login/logout flows
- Token storage and validation
- Token expiry monitoring
- Error handling
- Role-based access
- Multi-tenant context

See `useAuth.test.ts` for examples.

## Related Hooks

- `useSSE`: Real-time order updates via Server-Sent Events
- `useOrderUpdates`: Reactive order state management
- `useOrderPolling`: Polling fallback for real-time updates
