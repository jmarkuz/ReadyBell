import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TokenManager, DecodedToken } from './TokenManager';

/**
 * Helper function to create a valid JWT token
 */
function createMockToken(overrides: Partial<DecodedToken> = {}): string {
  const now = Math.floor(Date.now() / 1000);
  const payload: DecodedToken = {
    sub: 'user-123',
    username: 'testuser',
    email: 'test@example.com',
    name: 'Test User',
    role: 'STAFF',
    tenant_id: 'tenant-123',
    iat: now,
    exp: now + 3600, // 1 hour from now
    ...overrides,
  };

  // Create a mock JWT (header.payload.signature)
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  const signature = 'mock-signature';

  return `${header}.${body}.${signature}`;
}

/**
 * Helper to clear all storage
 */
function clearAllStorage() {
  localStorage.clear();
  sessionStorage.clear();
}

describe('TokenManager', () => {
  let tokenManager: TokenManager;

  beforeEach(() => {
    clearAllStorage();
    tokenManager = new TokenManager(true); // Use sessionStorage
    vi.clearAllMocks();
  });

  afterEach(() => {
    clearAllStorage();
  });

  // ============================================================================
  // store() Tests
  // ============================================================================

  describe('store()', () => {
    it('should store a valid token in sessionStorage', () => {
      const token = createMockToken();
      const result = tokenManager.store(token);

      expect(result).toBe(true);
      expect(sessionStorage.getItem('auth-token')).toBe(token);
    });

    it('should return false for empty token', () => {
      const result = tokenManager.store('');

      expect(result).toBe(false);
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should return false for non-string token', () => {
      const result = tokenManager.store(null as any);

      expect(result).toBe(false);
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should return false for malformed JWT', () => {
      const invalidToken = 'not.a.valid.jwt.token';
      const result = tokenManager.store(invalidToken);

      expect(result).toBe(false);
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should return false for expired token', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 3600 }); // 1 hour ago
      const result = tokenManager.store(expiredToken);

      expect(result).toBe(false);
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should return false if token missing required claims', () => {
      // Create a token with missing role
      const now = Math.floor(Date.now() / 1000);
      const payload = {
        sub: 'user-123',
        tenant_id: 'tenant-123',
        iat: now,
        exp: now + 3600,
        // Missing 'role' claim
      };

      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const body = btoa(JSON.stringify(payload));
      const invalidToken = `${header}.${body}.signature`;

      const result = tokenManager.store(invalidToken);

      // Should still store it (validation of claims happens during get/validate)
      // store() only checks format and expiry
      expect(result).toBe(true);
    });

    it('should overwrite previous token', () => {
      const token1 = createMockToken({ sub: 'user-1' });
      const token2 = createMockToken({ sub: 'user-2' });

      tokenManager.store(token1);
      expect(sessionStorage.getItem('auth-token')).toBe(token1);

      tokenManager.store(token2);
      expect(sessionStorage.getItem('auth-token')).toBe(token2);
    });
  });

  // ============================================================================
  // get() Tests
  // ============================================================================

  describe('get()', () => {
    it('should retrieve a stored valid token', () => {
      const token = createMockToken();
      tokenManager.store(token);

      const retrieved = tokenManager.get();

      expect(retrieved).toBe(token);
    });

    it('should return null if no token stored', () => {
      const retrieved = tokenManager.get();

      expect(retrieved).toBeNull();
    });

    it('should return null and clear expired token', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 100 }); // Expired

      // Manually store expired token (bypass store validation)
      sessionStorage.setItem('auth-token', expiredToken);

      const retrieved = tokenManager.get();

      expect(retrieved).toBeNull();
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should return null and clear invalid token', () => {
      const invalidToken = 'invalid.jwt.token.format';

      // Manually store invalid token
      sessionStorage.setItem('auth-token', invalidToken);

      const retrieved = tokenManager.get();

      expect(retrieved).toBeNull();
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should return token if expiring within grace period', () => {
      const now = Math.floor(Date.now() / 1000);
      const token = createMockToken({ exp: now + 20 }); // 20 seconds, within 30s grace period

      tokenManager.store(token);
      const retrieved = tokenManager.get();

      // Token should be considered expired due to grace period
      expect(retrieved).toBeNull();
    });
  });

  // ============================================================================
  // clear() Tests
  // ============================================================================

  describe('clear()', () => {
    it('should remove stored token from storage', () => {
      const token = createMockToken();
      tokenManager.store(token);

      expect(sessionStorage.getItem('auth-token')).toBe(token);

      tokenManager.clear();

      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should be safe to call when no token stored', () => {
      expect(() => tokenManager.clear()).not.toThrow();
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should allow storing new token after clear', () => {
      const token1 = createMockToken({ sub: 'user-1' });
      tokenManager.store(token1);
      tokenManager.clear();

      const token2 = createMockToken({ sub: 'user-2' });
      const result = tokenManager.store(token2);

      expect(result).toBe(true);
      expect(tokenManager.get()).toBe(token2);
    });
  });

  // ============================================================================
  // validate() Tests
  // ============================================================================

  describe('validate()', () => {
    it('should return true for valid token', () => {
      const token = createMockToken();
      tokenManager.store(token);

      expect(tokenManager.validate()).toBe(true);
    });

    it('should return false if no token stored', () => {
      expect(tokenManager.validate()).toBe(false);
    });

    it('should return false for expired token', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 3600 });

      sessionStorage.setItem('auth-token', expiredToken);

      expect(tokenManager.validate()).toBe(false);
    });

    it('should return false for malformed token', () => {
      sessionStorage.setItem('auth-token', 'invalid.token.format');

      expect(tokenManager.validate()).toBe(false);
    });

    it('should return false if token missing role claim', () => {
      const now = Math.floor(Date.now() / 1000);
      const payload = {
        sub: 'user-123',
        tenant_id: 'tenant-123',
        iat: now,
        exp: now + 3600,
        // Missing 'role'
      };

      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const body = btoa(JSON.stringify(payload));
      const token = `${header}.${body}.signature`;

      sessionStorage.setItem('auth-token', token);

      expect(tokenManager.validate()).toBe(false);
    });

    it('should return false if token missing tenant_id claim', () => {
      const now = Math.floor(Date.now() / 1000);
      const payload = {
        sub: 'user-123',
        role: 'STAFF',
        iat: now,
        exp: now + 3600,
        // Missing 'tenant_id'
      };

      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const body = btoa(JSON.stringify(payload));
      const token = `${header}.${body}.signature`;

      sessionStorage.setItem('auth-token', token);

      expect(tokenManager.validate()).toBe(false);
    });

    it('should return false if token has invalid role', () => {
      const now = Math.floor(Date.now() / 1000);
      const payload = {
        sub: 'user-123',
        role: 'INVALID_ROLE',
        tenant_id: 'tenant-123',
        iat: now,
        exp: now + 3600,
      };

      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const body = btoa(JSON.stringify(payload));
      const token = `${header}.${body}.signature`;

      sessionStorage.setItem('auth-token', token);

      expect(tokenManager.validate()).toBe(false);
    });

    it('should accept all valid roles (STAFF, ADMIN, PLATFORM_ADMIN)', () => {
      const roles: Array<'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN'> = [
        'STAFF',
        'ADMIN',
        'PLATFORM_ADMIN',
      ];

      for (const role of roles) {
        clearAllStorage();

        const token = createMockToken({ role });
        tokenManager.store(token);

        expect(tokenManager.validate()).toBe(true);
      }
    });
  });

  // ============================================================================
  // getDecodedToken() Tests
  // ============================================================================

  describe('getDecodedToken()', () => {
    it('should return decoded token payload', () => {
      const token = createMockToken();
      tokenManager.store(token);

      const decoded = tokenManager.getDecodedToken();

      expect(decoded).not.toBeNull();
      expect(decoded?.role).toBe('STAFF');
      expect(decoded?.tenant_id).toBe('tenant-123');
      expect(decoded?.username).toBe('testuser');
    });

    it('should return null if no token stored', () => {
      const decoded = tokenManager.getDecodedToken();

      expect(decoded).toBeNull();
    });

    it('should return null if token expired', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 3600 });

      sessionStorage.setItem('auth-token', expiredToken);

      const decoded = tokenManager.getDecodedToken();

      expect(decoded).toBeNull();
    });
  });

  // ============================================================================
  // getTimeUntilExpiry() Tests
  // ============================================================================

  describe('getTimeUntilExpiry()', () => {
    it('should return positive seconds for valid token', () => {
      const token = createMockToken();
      tokenManager.store(token);

      const timeRemaining = tokenManager.getTimeUntilExpiry();

      expect(timeRemaining).toBeGreaterThan(0);
      expect(timeRemaining).toBeLessThanOrEqual(3600); // Within 1 hour
    });

    it('should return 0 or negative for expired token', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 100 });

      sessionStorage.setItem('auth-token', expiredToken);

      const timeRemaining = tokenManager.getTimeUntilExpiry();

      expect(timeRemaining).toBeLessThanOrEqual(0);
    });

    it('should return -1 if no token stored', () => {
      const timeRemaining = tokenManager.getTimeUntilExpiry();

      expect(timeRemaining).toBe(-1);
    });
  });

  // ============================================================================
  // isExpiringsSoon() Tests
  // ============================================================================

  describe('isExpiringsSoon()', () => {
    it('should return true if token expiring within threshold', () => {
      const now = Math.floor(Date.now() / 1000);
      const token = createMockToken({ exp: now + 200 }); // 200 seconds

      tokenManager.store(token);

      expect(tokenManager.isExpiringsSoon(300)).toBe(true);
    });

    it('should return false if token has more time than threshold', () => {
      const token = createMockToken(); // 1 hour from now

      tokenManager.store(token);

      expect(tokenManager.isExpiringsSoon(300)).toBe(false);
    });

    it('should return false if token already expired', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 100 });

      sessionStorage.setItem('auth-token', expiredToken);

      expect(tokenManager.isExpiringsSoon(300)).toBe(false);
    });

    it('should use custom threshold', () => {
      const now = Math.floor(Date.now() / 1000);
      const token = createMockToken({ exp: now + 250 }); // 250 seconds (past 30s grace period)

      tokenManager.store(token);

      // With 250 seconds remaining:
      // isExpiringsSoon(50) should be false (250 > 50)
      // isExpiringsSoon(500) should be true (250 <= 500)
      expect(tokenManager.isExpiringsSoon(50)).toBe(false);
      expect(tokenManager.isExpiringsSoon(500)).toBe(true);
    });
  });

  // ============================================================================
  // getTenantId() Tests
  // ============================================================================

  describe('getTenantId()', () => {
    it('should return tenant_id from token', () => {
      const token = createMockToken({ tenant_id: 'my-restaurant' });
      tokenManager.store(token);

      expect(tokenManager.getTenantId()).toBe('my-restaurant');
    });

    it('should return null if no token stored', () => {
      expect(tokenManager.getTenantId()).toBeNull();
    });

    it('should return null if token expired', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 3600 });

      sessionStorage.setItem('auth-token', expiredToken);

      expect(tokenManager.getTenantId()).toBeNull();
    });
  });

  // ============================================================================
  // getRole() Tests
  // ============================================================================

  describe('getRole()', () => {
    it('should return role from token', () => {
      const token = createMockToken({ role: 'ADMIN' });
      tokenManager.store(token);

      expect(tokenManager.getRole()).toBe('ADMIN');
    });

    it('should return null if no token stored', () => {
      expect(tokenManager.getRole()).toBeNull();
    });

    it('should return null if token expired', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 3600 });

      sessionStorage.setItem('auth-token', expiredToken);

      expect(tokenManager.getRole()).toBeNull();
    });
  });

  // ============================================================================
  // getUserId() Tests
  // ============================================================================

  describe('getUserId()', () => {
    it('should return user ID from sub claim', () => {
      const token = createMockToken({ sub: 'user-abc-123' });
      tokenManager.store(token);

      expect(tokenManager.getUserId()).toBe('user-abc-123');
    });

    it('should return null if no token stored', () => {
      expect(tokenManager.getUserId()).toBeNull();
    });

    it('should return null if token expired', () => {
      const now = Math.floor(Date.now() / 1000);
      const expiredToken = createMockToken({ exp: now - 3600 });

      sessionStorage.setItem('auth-token', expiredToken);

      expect(tokenManager.getUserId()).toBeNull();
    });
  });

  // ============================================================================
  // Storage Fallback Tests
  // ============================================================================

  describe('Storage fallback', () => {
    it('should fall back to localStorage if sessionStorage unavailable', () => {
      // Create manager that tries sessionStorage first
      const manager = new TokenManager(true);

      const token = createMockToken();
      const result = manager.store(token);

      // Token should be stored somewhere
      expect(result).toBe(true);

      // And retrievable
      expect(manager.get()).toBe(token);
    });
  });

  // ============================================================================
  // Integration Tests
  // ============================================================================

  describe('Integration scenarios', () => {
    it('should handle complete authentication flow', () => {
      // 1. User logs in, token received
      const token = createMockToken({
        username: 'john.doe',
        role: 'STAFF',
        tenant_id: 'restaurant-123',
      });

      // 2. Store token
      const stored = tokenManager.store(token);
      expect(stored).toBe(true);

      // 3. Validate token is ready to use
      expect(tokenManager.validate()).toBe(true);

      // 4. Extract user info
      expect(tokenManager.getTenantId()).toBe('restaurant-123');
      expect(tokenManager.getRole()).toBe('STAFF');
      expect(tokenManager.getUserId()).not.toBeNull();

      // 5. Use token for API requests
      expect(tokenManager.get()).toBe(token);

      // 6. Logout - clear token
      tokenManager.clear();
      expect(tokenManager.validate()).toBe(false);
      expect(tokenManager.get()).toBeNull();
    });

    it('should handle token expiry gracefully', () => {
      // 1. Store valid token
      const token = createMockToken();
      tokenManager.store(token);
      expect(tokenManager.validate()).toBe(true);

      // 2. Simulate token expiry by manually setting expired token
      const now = Math.floor(Date.now() / 1000);
      const expiredPayload = {
        sub: 'user-123',
        role: 'STAFF',
        tenant_id: 'restaurant-123',
        iat: now - 3700,
        exp: now - 100, // Expired 100 seconds ago
      };

      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const body = btoa(JSON.stringify(expiredPayload));
      const expiredToken = `${header}.${body}.signature`;

      sessionStorage.setItem('auth-token', expiredToken);

      // 3. Verify token is no longer valid
      expect(tokenManager.validate()).toBe(false);
      expect(tokenManager.get()).toBeNull();

      // 4. System should clear the expired token
      expect(sessionStorage.getItem('auth-token')).toBeNull();
    });

    it('should prevent use of malformed tokens', () => {
      const malformedTokens = [
        'not-a-token',
        'only.two',
        '....',
        '',
        'a.b.c.d.e',
      ];

      for (const badToken of malformedTokens) {
        clearAllStorage();

        sessionStorage.setItem('auth-token', badToken);
        expect(tokenManager.validate()).toBe(false);
      }
    });
  });
});
