/**
 * AuthProvider Component
 * 
 * Provides authentication context to the entire app.
 * Handles token validation on app initialization and redirects to login if needed.
 * 
 * Features:
 * - Token validation on mount (checks expiry, required claims)
 * - Automatic redirect to login if token is invalid/expired
 * - Shows loading UI while validating token
 * - Provides useAuth hook for components to access auth state
 * 
 * Validates: AC1.1.6 Requirements:
 * - Token validation runs on app mount (before showing main UI)
 * - Valid token restores authenticated state
 * - Invalid/expired token redirects to login page
 * - Loading UI shown during validation
 * - App component updated with useAuth integration
 */

import { isPublicCustomerPath } from '@/utils/publicPaths';
import React, { createContext, ReactNode, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageLoader } from '@/components/PageLoader';
import { tokenManager } from '@/utils/TokenManager';

/**
 * Auth context provides authentication state and actions to all child components
 */
interface AuthContextType {
  isInitialized: boolean;
  isAuthenticated: boolean;
  isValidating: boolean;
  error: string | null;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

/**
 * AuthProvider Component
 * 
 * Wraps the entire app and handles:
 * 1. Token validation on mount
 * 2. Automatic redirect to login if token is invalid
 * 3. Loading UI display during validation
 * 4. Session restoration from stored token
 * 
 * Usage:
 * ```tsx
 * <AuthProvider>
 *   <App />
 * </AuthProvider>
 * ```
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const navigate = useNavigate();

  const [isValidating, setIsValidating] = useState(true);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  /**
   * Validate token on component mount
   * - Check if token exists and is valid
   * - Redirect to login if token is invalid/expired
   * - Show loading UI while validating
   */
  useEffect(() => {
    const validateTokenOnMount = async () => {
      // Customers on a tracking page never sign in
      if (isPublicCustomerPath(window.location.pathname)) {
        setIsValidating(false);
        setIsInitialized(true);
        return;
      }

      try {
        setIsValidating(true);
        setValidationError(null);

        // Get stored token
        const token = tokenManager.get();

        if (!token) {
          // No token found - user needs to login
          console.debug('[AuthProvider] No stored token found, redirecting to login');
          setIsValidating(false);
          setIsInitialized(true);
          
          // Only redirect if we're not already on the login page
          if (window.location.pathname !== '/login') {
            navigate('/login', { replace: true });
          }
          return;
        }

        // Token exists, check if it's valid
        const isValid = tokenManager.validate();

        if (!isValid) {
          // Token is invalid or expired
          console.warn('[AuthProvider] Stored token is invalid/expired, redirecting to login');
          tokenManager.clear();
          setValidationError('Your session has expired. Please log in again.');
          setIsValidating(false);
          setIsInitialized(true);
          navigate('/login', { replace: true });
          return;
        }

        // Token is valid - restore authenticated state
        console.debug('[AuthProvider] Token validation successful, authenticated state restored');
        setIsAuthenticated(true);
        setIsValidating(false);
        setIsInitialized(true);
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        console.error('[AuthProvider] Token validation failed:', errorMessage);
        setValidationError(errorMessage);
        setIsValidating(false);
        setIsInitialized(true);
        
        // Clear invalid token and redirect to login
        tokenManager.clear();
        navigate('/login', { replace: true });
      }
    };

    // Run validation on mount
    validateTokenOnMount();
  }, [navigate]);

  // While validating token, show loading UI only if on authenticated routes (not on login)
  // Check current pathname to avoid showing loader on login page
  const isOnLoginPage =
    window.location.pathname === '/login' || isPublicCustomerPath(window.location.pathname);
  
  if (isValidating && !isOnLoginPage) {
    return (
      <div
        className="flex items-center justify-center min-h-screen bg-slate-50 dark:bg-slate-950"
        role="status"
        aria-live="polite"
        aria-label="Validating authentication token"
      >
        <PageLoader />
      </div>
    );
  }

  // After validation, provide context to children
  return (
    <AuthContext.Provider
      value={{
        isInitialized,
        isAuthenticated,
        isValidating,
        error: validationError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Hook to access auth context
 * 
 * @returns Auth context with initialization status and authentication state
 * @throws Error if used outside of AuthProvider
 */
export function useAuthContext(): AuthContextType {
  const context = React.useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuthContext must be used within AuthProvider');
  }
  return context;
}

export default AuthProvider;
