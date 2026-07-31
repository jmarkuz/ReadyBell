import { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { SearchProvider } from '@/context/SearchContext';
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
 * App Root Component
 * 
 * Sets up:
 * - Theme provider for light/dark theme support with persistence
 * - Search context for global search state
 * - Router with Layout and lazy-loaded page routes
 * - Suspense boundaries with PageLoader fallback for code-split pages
 * 
 * Route structure:
 * - / (Dashboard)
 * - /orders (Orders)
 * - /menu (Menu Management)
 * - /payments (Payments)
 * - /analytics (Analytics)
 * - /settings (Settings)
 * - /support (Support)
 */
function App() {
  return (
    <ThemeProvider defaultMode="light" persistToLocalStorage>
      <SearchProvider>
        <Router>
          <Routes>
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
        </Router>
      </SearchProvider>
    </ThemeProvider>
  );
}

export default App;
