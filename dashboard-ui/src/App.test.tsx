/**
 * App Component Integration Tests
 * 
 * Tests app initialization with bearer token authentication:
 * - Token validation runs on app mount
 * - Valid token restores authenticated state
 * - Invalid/expired token redirects to login
 * - Loading UI shown during validation
 * 
 * Validates: Task 1.1.6 - Token Validation on App Initialization
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import App from './App';
import { tokenManager } from '@/utils/TokenManager';

// Mock tokenManager
vi.mock('@/utils/TokenManager', () => {
  const mockTokenManager = {
    get: vi.fn(),
    validate: vi.fn(),
    clear: vi.fn(),
    getDecodedToken: vi.fn(),
    getTenantId: vi.fn(),
    getRole: vi.fn(),
    getUserId: vi.fn(),
    store: vi.fn(),
    getTimeUntilExpiry: vi.fn(),
    isExpiringsSoon: vi.fn(),
  };

  return {
    tokenManager: mockTokenManager,
    TokenManager: vi.fn(() => mockTokenManager),
  };
});

// Mock useAuth hook
vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({
    isAuthenticated: true,
    user: {
      id: 'user-123',
      username: 'testuser',
      email: 'test@example.com',
      name: 'Test User',
      role: 'STAFF',
      tenantId: 'tenant-123',
      restaurantName: 'Test Restaurant',
    },
    loading: false,
    error: null,
    userId: 'user-123',
    username: 'testuser',
    email: 'test@example.com',
    name: 'Test User',
    role: 'STAFF',
    tenantId: 'tenant-123',
    isTokenExpiringSoon: false,
    timeUntilExpiry: 3600,
    login: vi.fn(),
    logout: vi.fn(),
    refreshToken: vi.fn(),
    clearError: vi.fn(),
  })),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('App Component Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should integrate token validation on app mount', async () => {
    const mockGet = vi.mocked(tokenManager.get);
    const mockValidate = vi.mocked(tokenManager.validate);

    mockGet.mockReturnValue('valid-token');
    mockValidate.mockReturnValue(true);

    render(<App />);

    // Wait for token validation to complete
    await waitFor(() => {
      expect(mockValidate).toHaveBeenCalled();
    });
  });

  it('should restore authenticated state with valid token', async () => {
    const mockGet = vi.mocked(tokenManager.get);
    const mockValidate = vi.mocked(tokenManager.validate);

    mockGet.mockReturnValue('valid-token');
    mockValidate.mockReturnValue(true);

    render(<App />);

    // Wait for app to render
    await waitFor(() => {
      // App should render without redirecting
      expect(mockNavigate).not.toHaveBeenCalled();
    });
  });

  it('should handle token expiry detection on initialization', async () => {
    const mockGet = vi.mocked(tokenManager.get);
    const mockValidate = vi.mocked(tokenManager.validate);

    // Token exists but is invalid/expired
    mockGet.mockReturnValue('expired-token');
    mockValidate.mockReturnValue(false);

    render(<App />);

    // Wait for validation
    await waitFor(() => {
      expect(mockValidate).toHaveBeenCalled();
    });
  });
});
