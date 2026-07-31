/**
 * Tests for useAuth Hook
 * 
 * Tests cover:
 * - Initial state when no token is stored
 * - Loading state during initialization
 * - User profile extraction from token
 * - Login action and token storage
 * - Logout action and token cleanup
 * - Token expiry monitoring
 * - Error handling
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { useAuth } from './useAuth';
import { tokenManager } from '@/utils/TokenManager';
import * as apiModule from '@/utils/api';

// Mock dependencies
vi.mock('@/utils/TokenManager');
vi.mock('@/utils/api');

// Mock token for testing
const MOCK_TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsInVzZXJuYW1lIjoiam9obiIsImVtYWlsIjoiam9obkBleGFtcGxlLmNvbSIsIm5hbWUiOiJKb2huIERvZSIsInJvbGUiOiJTVEFGRiIsInRlbmFudF9pZCI6InRlbmFudC00NTYiLCJpYXQiOjE2OTI2MzM2MDAsImV4cCI6MTY5MjYzNzIwMH0.dGVzdC1zaWduYXR1cmU';

const MOCK_EXPIRED_TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsInVzZXJuYW1lIjoiam9obiIsImVtYWlsIjoiam9obkBleGFtcGxlLmNvbSIsIm5hbWUiOiJKb2huIERvZSIsInJvbGUiOiJTVEFGRiIsInRlbmFudF9pZCI6InRlbmFudC00NTYiLCJpYXQiOjE2OTI2MzM2MDAsImV4cCI6MTY5MjYzMzYwMH0.dGVzdC1zaWduYXR1cmU';

const MOCK_USER_PROFILE = {
  id: 'user-123',
  username: 'john',
  email: 'john@example.com',
  name: 'John Doe',
  role: 'STAFF' as const,
  tenantId: 'tenant-456',
  restaurantName: 'Test Restaurant',
};

describe('useAuth Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    
    // Setup default mocks
    vi.mocked(tokenManager).get.mockReturnValue(null);
    vi.mocked(tokenManager).getDecodedToken.mockReturnValue(null);
    vi.mocked(tokenManager).store.mockReturnValue(true);
    vi.mocked(tokenManager).clear.mockReturnValue(undefined);
    vi.mocked(tokenManager).validate.mockReturnValue(false);
    vi.mocked(tokenManager).getTimeUntilExpiry.mockReturnValue(-1);
    vi.mocked(tokenManager).isExpiringsSoon.mockReturnValue(false);
  });

  afterEach(() => {
    // Real timers - no need to reset
  });

  // =========================================================================
  // Initial State Tests
  // =========================================================================

  describe('Initial State', () => {
    it('should start with unauthenticated state when no token exists', async () => {
      const { result } = renderHook(() => useAuth({ validateOnMount: true }));

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
    });

    it('should validate token on mount when validateOnMount=true', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { result } = renderHook(() => useAuth({ validateOnMount: true }));

      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });

      expect(result.current.user).toMatchObject({
        id: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenantId: 'tenant-456',
      });
    });

    it('should provide user info shortcuts', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'ADMIN',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { result } = renderHook(() => useAuth());

      await waitFor(() => {
        expect(result.current.userId).toBe('user-123');
      });

      expect(result.current.username).toBe('john');
      expect(result.current.email).toBe('john@example.com');
      expect(result.current.name).toBe('John Doe');
      expect(result.current.role).toBe('ADMIN');
      expect(result.current.tenantId).toBe('tenant-456');
    });
  });

  // =========================================================================
  // Login Tests
  // =========================================================================

  describe('Login Action', () => {
    it('should login successfully with valid credentials', async () => {
      vi.mocked(apiModule.apiClient.login).mockResolvedValue({
        token: MOCK_TOKEN,
        refreshToken: 'refresh-token-123',
      });
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { result } = renderHook(() => useAuth({ validateOnMount: false }));

      await act(async () => {
        await result.current.login({
          username: 'john',
          password: 'password123',
        });
      });

      expect(apiModule.apiClient.login).toHaveBeenCalledWith({
        username: 'john',
        password: 'password123',
      });
      expect(tokenManager.store).toHaveBeenCalledWith(MOCK_TOKEN);
      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.error).toBeNull();
    });

    it('should handle login failure', async () => {
      vi.mocked(apiModule.apiClient.login).mockRejectedValue(
        new Error('Invalid credentials')
      );

      const { result } = renderHook(() => useAuth({ validateOnMount: false }));

      await act(async () => {
        try {
          await result.current.login({
            username: 'john',
            password: 'wrong-password',
          });
        } catch {
          // Expected error
        }
      });

      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
      expect(result.current.error).toBe('Invalid credentials');
    });

    it('should handle missing token in login response', async () => {
      vi.mocked(apiModule.apiClient.login).mockResolvedValue({
        token: '',
        refreshToken: 'refresh-token-123',
      });

      const { result } = renderHook(() => useAuth({ validateOnMount: false }));

      await act(async () => {
        try {
          await result.current.login({
            username: 'john',
            password: 'password123',
          });
        } catch {
          // Expected error
        }
      });

      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.error).toContain('No token');
    });
  });

  // =========================================================================
  // Logout Tests
  // =========================================================================

  describe('Logout Action', () => {
    it('should logout successfully and clear state', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { result } = renderHook(() => useAuth());

      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });

      act(() => {
        result.current.logout();
      });

      expect(tokenManager.clear).toHaveBeenCalled();
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
      expect(result.current.error).toBeNull();
    });
  });

  // =========================================================================
  // Token Expiry Tests
  // =========================================================================

  describe('Token Expiry Monitoring', () => {
    it('should track time until token expiry', async () => {
      const futureExp = Math.floor(Date.now() / 1000) + 3600;
      
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: futureExp,
      });
      vi.mocked(tokenManager).getTimeUntilExpiry.mockReturnValue(3600);

      const { result } = renderHook(() => useAuth());

      await waitFor(() => {
        expect(result.current.timeUntilExpiry).toBeGreaterThan(0);
      });
    });

    it('should detect when token is expiring soon', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 100,
      });
      vi.mocked(tokenManager).isExpiringsSoon.mockReturnValue(true);

      const { result } = renderHook(() => useAuth({ autoRefreshThreshold: 300 }));

      await waitFor(() => {
        expect(result.current.isTokenExpiringSoon).toBe(true);
      });
    });

    it('should cleanup timers on unmount', () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { unmount } = renderHook(() => useAuth());
      
      // Should not throw or have memory leaks
      expect(() => unmount()).not.toThrow();
    });
  });

  // =========================================================================
  // Error Handling Tests
  // =========================================================================

  describe('Error Handling', () => {
    it('should clear error message with clearError action', async () => {
      vi.mocked(apiModule.apiClient.login).mockRejectedValue(
        new Error('Login failed')
      );

      const { result } = renderHook(() => useAuth({ validateOnMount: false }));

      await act(async () => {
        try {
          await result.current.login({
            username: 'john',
            password: 'wrong',
          });
        } catch {
          // Expected
        }
      });

      await waitFor(() => {
        expect(result.current.error).not.toBeNull();
      });

      act(() => {
        result.current.clearError();
      });

      expect(result.current.error).toBeNull();
    });

    it('should handle invalid token gracefully', async () => {
      vi.mocked(tokenManager).get.mockReturnValue('invalid-token');
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue(null);

      const { result } = renderHook(() => useAuth());

      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(false);
      });

      expect(result.current.user).toBeNull();
      expect(tokenManager.clear).toHaveBeenCalled();
    });
  });

  // =========================================================================
  // Token Refresh Tests
  // =========================================================================

  describe('Token Refresh', () => {
    it('should refresh token successfully', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });
      
      const newToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsInVzZXJuYW1lIjoiam9obiIsImVtYWlsIjoiam9obkBleGFtcGxlLmNvbSIsIm5hbWUiOiJKb2huIERvZSIsInJvbGUiOiJTVEFGRiIsInRlbmFudF9pZCI6InRlbmFudC00NTYiLCJpYXQiOjE2OTI2MzM2MDAsImV4cCI6MTY5MjY0MDgwMH0.dGVzdC1zaWduYXR1cmU';
      
      vi.mocked(apiModule.apiClient.login).mockResolvedValue({
        token: newToken,
        refreshToken: 'new-refresh-token',
      });
      vi.mocked(tokenManager).store.mockReturnValue(true);

      const { result } = renderHook(() => useAuth());

      let refreshResult = false;
      await act(async () => {
        refreshResult = await result.current.refreshToken();
      });

      expect(refreshResult).toBe(true);
      expect(tokenManager.store).toHaveBeenCalledWith(newToken);
    });

    it('should logout on token refresh failure', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });
      
      vi.mocked(apiModule.apiClient.login).mockRejectedValue(
        new Error('Refresh token expired')
      );

      const { result } = renderHook(() => useAuth());

      await waitFor(() => {
        expect(result.current.isAuthenticated).toBe(true);
      });

      let refreshResult = false;
      await act(async () => {
        refreshResult = await result.current.refreshToken();
      });

      expect(refreshResult).toBe(false);
      expect(result.current.isAuthenticated).toBe(false);
      expect(tokenManager.clear).toHaveBeenCalled();
    });
  });

  // =========================================================================
  // Role-Based Tests
  // =========================================================================

  describe('Role-Based Access', () => {
    it('should provide different roles for different users', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      
      // First test with STAFF role
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { result: staffResult } = renderHook(() => useAuth());
      
      await waitFor(() => {
        expect(staffResult.current.role).toBe('STAFF');
      });

      // Test with ADMIN role
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-789',
        username: 'admin',
        email: 'admin@example.com',
        name: 'Admin User',
        role: 'ADMIN',
        tenant_id: 'tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { result: adminResult } = renderHook(() => useAuth());
      
      await waitFor(() => {
        expect(adminResult.current.role).toBe('ADMIN');
      });
    });

    it('should track tenant ID for multi-tenant isolation', async () => {
      vi.mocked(tokenManager).get.mockReturnValue(MOCK_TOKEN);
      vi.mocked(tokenManager).getDecodedToken.mockReturnValue({
        sub: 'user-123',
        username: 'john',
        email: 'john@example.com',
        name: 'John Doe',
        role: 'STAFF',
        tenant_id: 'restaurant-tenant-456',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      });

      const { result } = renderHook(() => useAuth());

      await waitFor(() => {
        expect(result.current.tenantId).toBe('restaurant-tenant-456');
      });
    });
  });
});
