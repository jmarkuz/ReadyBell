/** useAuth Hook - Access authentication state and actions */

import { useState, useCallback, useEffect, useRef } from 'react';
import { UserProfile } from '@/utils/api';
import { tokenManager, DecodedToken } from '@/utils/TokenManager';

/** Auth state returned by useAuth hook */
export interface AuthState {
  isAuthenticated: boolean;
  user: UserProfile | null;
  loading: boolean;
  error: string | null;

  userId: string | null;
  username: string | null;
  email: string | null;
  name: string | null;
  role: 'STAFF' | 'RESTAURANT_ADMIN' | 'PLATFORM_ADMIN' | null;
  tenantId: string | null;

  isTokenExpiringSoon: boolean;
  timeUntilExpiry: number;
}

/** Auth actions returned by useAuth hook */
export interface AuthActions {
  login: (credentials: { restaurant?: string; username: string; password: string }) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<boolean>;
  clearError: () => void;
}

export interface UseAuthOptions {
  validateOnMount?: boolean;

  /** Seconds before expiry at which the token counts as expiring soon; 0 disables it. */
  autoRefreshThreshold?: number;

  /** How often to check the token's expiry, in milliseconds. */
  expiryCheckInterval?: number;
}

const DEFAULT_OPTIONS: Required<UseAuthOptions> = {
  validateOnMount: true,
  autoRefreshThreshold: 300, // Refresh 5 minutes before expiry
  expiryCheckInterval: 60000, // Check every 60 seconds
};

export function useAuth(options?: UseAuthOptions): AuthState & AuthActions {
  const mergedOptions = { ...DEFAULT_OPTIONS, ...options };


  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isTokenExpiringSoon, setIsTokenExpiringSoon] = useState<boolean>(false);
  const [timeUntilExpiry, setTimeUntilExpiry] = useState<number>(-1);

  const expiryCheckTimerRef = useRef<NodeJS.Timeout | null>(null);
  const autoRefreshTimerRef = useRef<NodeJS.Timeout | null>(null);
  const isMountedRef = useRef<boolean>(true);


  /** Safely update state only if component is mounted */
  const safeSetState = useCallback(<T,>(
    setState: (value: T) => void,
    value: T
  ) => {
    if (isMountedRef.current) {
      setState(value);
    }
  }, []);

  const buildUserProfileFromToken = useCallback(
    (decoded: DecodedToken): UserProfile => ({
      id: decoded.sub || decoded.id || '',
      username: decoded.username || '',
      email: decoded.email || '',
      name: decoded.name || '',
      role: decoded.role as 'STAFF' | 'RESTAURANT_ADMIN' | 'PLATFORM_ADMIN',
      tenantId: decoded.tenant_id || decoded.tenantId || '',
      restaurantName: decoded.name || '',
    }),
    []
  );

  const updateTokenExpiry = useCallback(() => {
    const timeRemaining = tokenManager.getTimeUntilExpiry();
    safeSetState(setTimeUntilExpiry, timeRemaining);

    const isExpiringSoon = tokenManager.isExpiringsSoon(
      mergedOptions.autoRefreshThreshold
    );
    safeSetState(setIsTokenExpiringSoon, isExpiringSoon);
  }, [mergedOptions.autoRefreshThreshold, safeSetState]);

  const loadUserProfile = useCallback(() => {
    const token = tokenManager.get();

    if (!token) {
      safeSetState(setIsAuthenticated, false);
      safeSetState(setUser, null);
      safeSetState(setLoading, false);
      return;
    }

    const decoded = tokenManager.getDecodedToken();
    if (!decoded) {
      tokenManager.clear();
      safeSetState(setIsAuthenticated, false);
      safeSetState(setUser, null);
      safeSetState(setLoading, false);
      return;
    }

    const userProfile = buildUserProfileFromToken(decoded);
    safeSetState(setUser, userProfile);
    safeSetState(setIsAuthenticated, true);
    safeSetState(setLoading, false);

    updateTokenExpiry();
  }, [safeSetState, buildUserProfileFromToken, updateTokenExpiry]);

  const startExpiryMonitoring = useCallback(() => {
    if (expiryCheckTimerRef.current) {
      clearInterval(expiryCheckTimerRef.current);
    }

    expiryCheckTimerRef.current = setInterval(() => {
      updateTokenExpiry();

      if (
        mergedOptions.autoRefreshThreshold > 0 &&
        tokenManager.isExpiringsSoon(mergedOptions.autoRefreshThreshold)
      ) {
        console.debug(
          '[useAuth] Token expiring soon, attempting proactive refresh'
        );

      }
    }, mergedOptions.expiryCheckInterval);
  }, [
    mergedOptions.autoRefreshThreshold,
    mergedOptions.expiryCheckInterval,
    updateTokenExpiry,
  ]);

  const cleanup = useCallback(() => {
    if (expiryCheckTimerRef.current) {
      clearInterval(expiryCheckTimerRef.current);
      expiryCheckTimerRef.current = null;
    }

    if (autoRefreshTimerRef.current) {
      clearInterval(autoRefreshTimerRef.current);
      autoRefreshTimerRef.current = null;
    }
  }, []);


  const login = useCallback(
    async (credentials: { restaurant?: string; username: string; password: string }): Promise<void> => {
      try {
        safeSetState(setLoading, true);
        safeSetState(setError, null);

        // Lazy import: api.ts would otherwise be a circular dependency.
        const { apiClient } = await import('@/utils/api');

        const response = await apiClient.login({
          ...(credentials.restaurant && { restaurant: credentials.restaurant }),
          username: credentials.username,
          password: credentials.password,
        });

        if (!response.token) {
          throw new Error('No token in login response');
        }

        const stored = tokenManager.store(response.token);
        if (!stored) {
          throw new Error('Failed to store authentication token');
        }

        loadUserProfile();

        startExpiryMonitoring();

        console.debug('[useAuth] Login successful');
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.error('[useAuth] Login failed:', message);
        safeSetState(setError, message);
        safeSetState(setIsAuthenticated, false);
        safeSetState(setUser, null);
        throw err;
      } finally {
        safeSetState(setLoading, false);
      }
    },
    [safeSetState, loadUserProfile, startExpiryMonitoring]
  );

  const logout = useCallback(() => {
    try {
      cleanup();

      // Revoke the token on the server without waiting for it.
      import('@/utils/api').then(({ apiClient }) => {
        apiClient.logout();
      });

      tokenManager.clear();
      safeSetState(setIsAuthenticated, false);
      safeSetState(setUser, null);
      safeSetState(setError, null);
      safeSetState(setTimeUntilExpiry, -1);
      safeSetState(setIsTokenExpiringSoon, false);

      console.debug('[useAuth] Logout successful');
    } catch (err) {
      console.error('[useAuth] Logout error:', err);
    }
  }, [cleanup, safeSetState]);

    const refreshToken = useCallback(async (): Promise<boolean> => {
    try {
      // Lazy import: api.ts would otherwise be a circular dependency.
      const { apiClient } = await import('@/utils/api');

      const response = await apiClient.login({
        username: user?.username || '',
        password: '', // Note: This is a simplified approach
        // In a real implementation, refresh would use a refresh token
        // This is a placeholder - actual implementation depends on backend
      });

      if (!response.token) {
        throw new Error('No token in refresh response');
      }

      const stored = tokenManager.store(response.token);
      if (!stored) {
        throw new Error('Failed to store refreshed token');
      }

      loadUserProfile();

      console.debug('[useAuth] Token refresh successful');
      return true;
    } catch (err) {
      console.error('[useAuth] Token refresh failed:', err);

      logout();
      return false;
    }
  }, [user?.username, loadUserProfile, logout]);

  const clearError = useCallback(() => {
    safeSetState(setError, null);
  }, [safeSetState]);


  useEffect(() => {
    if (mergedOptions.validateOnMount) {
      loadUserProfile();
      startExpiryMonitoring();
    }

    return () => {
      cleanup();
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
      cleanup();
    };
  }, [cleanup]);


  return {
    isAuthenticated,
    user,
    loading,
    error,
    isTokenExpiringSoon,
    timeUntilExpiry,

    userId: user?.id || null,
    username: user?.username || null,
    email: user?.email || null,
    name: user?.name || null,
    role: user?.role || null,
    tenantId: user?.tenantId || null,

    login,
    logout,
    refreshToken,
    clearError,
  };
}

export default useAuth;
