import { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { SearchProvider } from '@/context/SearchContext';
import { AuthProvider } from '@/context/AuthProvider';
import Layout from '@/components/Layout';
import { PageLoader } from '@/components/PageLoader';
import RequireRole from '@/components/RequireRole';

/**
 * Lazy-loaded page components for code splitting
 * 
 * Validates: AC1 (Split main bundle into chunks - header, sidebar, pages)
 * Validates: AC2 (Lazy load each page with React.lazy())
 * Validates: AC3 (Use Suspense for loading fallback)
 */
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const Staff = lazy(() => import('@/pages/Staff'));
const Settings = lazy(() => import('@/pages/Settings'));
const Restaurants = lazy(() => import('@/pages/Restaurants'));
const LoginPage = lazy(() => import('@/pages/Login'));
const TrackPage = lazy(() => import('@/pages/Track'));
const CounterClaimPage = lazy(() => import('@/pages/CounterClaim'));

/**
 * App Root Component
 * 
 * Sets up:
 * - Auth provider for bearer token authentication and validation on app init
 * - Theme provider for light/dark theme support with persistence
 * - Search context for global search state
 * - Router with Layout and lazy-loaded page routes
 * - Suspense boundaries with PageLoader fallback for code-split pages
 * 
 * Authentication Flow:
 * - On app initialization, AuthProvider validates stored bearer token
 * - If token is valid, authenticated state is restored (no redirect)
 * - If token is invalid/expired, user is redirected to /login
 * - While validating, PageLoader is shown (loading UI)
 * 
 * Route structure:
 * - / (Dashboard)
 * - /orders (Orders)
 * - /menu (Menu Management)
 * - /payments (Payments)
 * - /analytics (Analytics)
 * - /settings (Settings)
 * - /support (Support)
 * 
 * Validates: Task 1.1.6 - Token Validation on App Initialization
 * - Token validation runs on app mount (AuthProvider effect)
 * - Valid token restores authenticated state (Layout/pages render normally)
 * - Invalid/expired token redirects to /login (navigate() in AuthProvider)
 * - Loading UI shown during validation (PageLoader in AuthProvider)
 * - App component updated with useAuth integration (AuthProvider wraps app)
 */
function App() {
  return (
    <Router>
      <ThemeProvider defaultMode="light" persistToLocalStorage>
        <SearchProvider>
          <AuthProvider>
            <Routes>
              <Route
                path="/login"
                element={
                  <Suspense fallback={<PageLoader />}>
                    <LoginPage />
                  </Suspense>
                }
              />
              <Route
                path="/track/:sessionId"
                element={
                  <Suspense fallback={<PageLoader />}>
                    <TrackPage />
                  </Suspense>
                }
              />
              <Route
                path="/r/:identifier"
                element={
                  <Suspense fallback={<PageLoader />}>
                    <CounterClaimPage />
                  </Suspense>
                }
              />
              <Route element={<Layout />}>
                <Route
                  path="/"
                  element={
                    <RequireRole roles={['STAFF', 'RESTAURANT_ADMIN']}>
                      <Suspense fallback={<PageLoader />}>
                        <Dashboard />
                      </Suspense>
                    </RequireRole>
                  }
                />
                <Route
                  path="/staff"
                  element={
                    <RequireRole roles={['RESTAURANT_ADMIN']}>
                      <Suspense fallback={<PageLoader />}>
                        <Staff />
                      </Suspense>
                    </RequireRole>
                  }
                />
                <Route
                  path="/settings"
                  element={
                    <RequireRole roles={['RESTAURANT_ADMIN']}>
                      <Suspense fallback={<PageLoader />}>
                        <Settings />
                      </Suspense>
                    </RequireRole>
                  }
                />
                <Route
                  path="/restaurants"
                  element={
                    <RequireRole roles={['PLATFORM_ADMIN']}>
                      <Suspense fallback={<PageLoader />}>
                        <Restaurants />
                      </Suspense>
                    </RequireRole>
                  }
                />
              </Route>
            </Routes>
          </AuthProvider>
        </SearchProvider>
      </ThemeProvider>
    </Router>
  );
}

export default App;
