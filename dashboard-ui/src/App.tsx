import { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { SearchProvider } from '@/context/SearchContext';
import { AuthProvider } from '@/context/AuthProvider';
import Layout from '@/components/Layout';
import { PageLoader } from '@/components/PageLoader';
import './App.css';

/**
 * Lazy-loaded page components for code splitting
 * 
 * Validates: AC1 (Split main bundle into chunks - header, sidebar, pages)
 * Validates: AC2 (Lazy load each page with React.lazy())
 * Validates: AC3 (Use Suspense for loading fallback)
 */
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const Orders = lazy(() => import('@/pages/Orders'));
const Menu = lazy(() => import('@/pages/Menu'));
const Payments = lazy(() => import('@/pages/Payments'));
const Analytics = lazy(() => import('@/pages/Analytics'));
const Settings = lazy(() => import('@/pages/Settings'));
const Support = lazy(() => import('@/pages/Support'));
const LoginPage = lazy(() => import('@/pages/Login'));

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
              <Route element={<Layout />}>
                <Route 
                  path="/" 
                  element={
                    <Suspense fallback={<PageLoader />}>
                      <Dashboard />
                    </Suspense>
                  } 
                />
                <Route 
                  path="/orders" 
                  element={
                    <Suspense fallback={<PageLoader />}>
                      <Orders />
                    </Suspense>
                  } 
                />
                <Route 
                  path="/menu" 
                  element={
                    <Suspense fallback={<PageLoader />}>
                      <Menu />
                    </Suspense>
                  } 
                />
                <Route 
                  path="/payments" 
                  element={
                    <Suspense fallback={<PageLoader />}>
                      <Payments />
                    </Suspense>
                  } 
                />
                <Route 
                  path="/analytics" 
                  element={
                    <Suspense fallback={<PageLoader />}>
                      <Analytics />
                    </Suspense>
                  } 
                />
                <Route 
                  path="/settings" 
                  element={
                    <Suspense fallback={<PageLoader />}>
                      <Settings />
                    </Suspense>
                  } 
                />
                <Route 
                  path="/support" 
                  element={
                    <Suspense fallback={<PageLoader />}>
                      <Support />
                    </Suspense>
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
