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

/**
 * Login page component - simple placeholder for development
 */
const LoginPage = () => {
  const handleLogin = async () => {
    try {
      const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLXRlc3QiLCJ1c2VybmFtZSI6InRlc3QtdXNlciIsImVtYWlsIjoidGVzdEB0ZXN0LmNvbSIsIm5hbWUiOiJUZXN0IFVzZXIiLCJyb2xlIjoiU1RBRkYiLCJ0ZW5hbnRfaWQiOiJ0ZW5hbnQtMTIzIiwiZXhwIjoyNzE2MjM5MDIyfQ.7XQZ_7xWKb0O9xwK8j7G5zK5zK5zK5zK5zK5zK5z';
      
      // Import tokenManager and store the token
      const { tokenManager } = await import('@/utils/TokenManager');
      const success = tokenManager.store(mockToken);
      
      if (success) {
        console.log('[LoginPage] Token stored successfully');
        // Redirect to dashboard
        window.location.href = '/';
      } else {
        console.error('[LoginPage] Failed to store token');
        alert('Failed to sign in. Check console for details.');
      }
    } catch (error) {
      console.error('[LoginPage] Login error:', error);
      alert('Login failed: ' + (error instanceof Error ? error.message : String(error)));
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-slate-900 to-slate-800">
      <div className="w-full max-w-md p-8 bg-white rounded-lg shadow-xl">
        <h1 className="text-2xl font-bold text-center text-slate-900 mb-8">
          Dashboard Login
        </h1>
        <div className="space-y-4">
          <p className="text-center text-slate-600 mb-6">
            Development mode - using mock authentication
          </p>
          <button
            onClick={handleLogin}
            className="w-full py-2 px-4 bg-blue-600 text-white font-semibold rounded-lg hover:bg-blue-700 transition-colors"
          >
            Sign In (Dev)
          </button>
        </div>
      </div>
    </div>
  );
};

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
              <Route path="/login" element={<LoginPage />} />
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
