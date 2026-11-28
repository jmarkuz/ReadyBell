/** Validates the stored token on start-up, redirects to the login page when it is missing or invalid, and provides the auth context. */

import { isPublicCustomerPath } from '@/utils/publicPaths';
import React, { createContext, ReactNode, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageLoader } from '@/components/PageLoader';
import { tokenManager } from '@/utils/TokenManager';

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

export function AuthProvider({ children }: AuthProviderProps) {
  const navigate = useNavigate();

  const [isValidating, setIsValidating] = useState(true);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

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

        const token = tokenManager.get();

        if (!token) {
          console.debug('[AuthProvider] No stored token found, redirecting to login');
          setIsValidating(false);
          setIsInitialized(true);

          if (window.location.pathname !== '/login') {
            // the customer display is opened on its own screen: come back to it after signing in
            navigate(window.location.pathname === '/display' ? '/login?next=/display' : '/login', { replace: true });
          }
          return;
        }

        const isValid = tokenManager.validate();

        if (!isValid) {
          console.warn('[AuthProvider] Stored token is invalid/expired, redirecting to login');
          tokenManager.clear();
          setValidationError('Your session has expired. Please log in again.');
          setIsValidating(false);
          setIsInitialized(true);
          navigate('/login', { replace: true });
          return;
        }

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

        tokenManager.clear();
        navigate('/login', { replace: true });
      }
    };

    validateTokenOnMount();
  }, [navigate]);

  // No loader on the login page or the customer pages, which render without a session.
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

export function useAuthContext(): AuthContextType {
  const context = React.useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuthContext must be used within AuthProvider');
  }
  return context;
}

export default AuthProvider;
