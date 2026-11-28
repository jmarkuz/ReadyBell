import { Suspense, lazy } from 'react';
import { AlertProvider } from '@/context/AlertProvider';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { SearchProvider } from '@/context/SearchContext';
import { AuthProvider } from '@/context/AuthProvider';
import Layout from '@/components/Layout';
import { PageLoader } from '@/components/PageLoader';
import RequireRole from '@/components/RequireRole';

const Dashboard = lazy(() => import('@/pages/Dashboard'));
const Staff = lazy(() => import('@/pages/Staff'));
const Settings = lazy(() => import('@/pages/Settings'));
const Restaurants = lazy(() => import('@/pages/Restaurants'));
const LoginPage = lazy(() => import('@/pages/Login'));
const Display = lazy(() => import('@/pages/Display'));
const TrackPage = lazy(() => import('@/pages/Track'));

/** App Root Component */
function App() {
  return (
    <Router>
      <ThemeProvider defaultMode="light" persistToLocalStorage>
        <SearchProvider>
          <AuthProvider>
            <AlertProvider>
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
                  path="/display"
                  element={
                    <RequireRole roles={['STAFF', 'RESTAURANT_ADMIN']}>
                      <Suspense fallback={<PageLoader />}>
                        <Display />
                      </Suspense>
                    </RequireRole>
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
            </AlertProvider>
          </AuthProvider>
        </SearchProvider>
      </ThemeProvider>
    </Router>
  );
}

export default App;
