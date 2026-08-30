import { Loader2 } from 'lucide-react';

/**
 * PageLoader Component
 * 
 * Displays a loading spinner while lazy-loaded pages are being fetched.
 * Used as the fallback component for React.lazy() code splitting.
 * 
 * Validates: AC3 (Use Suspense for loading fallback)
 */
export function PageLoader() {
  return (
    <div
      className="flex items-center justify-center min-h-screen bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-50"
      role="status"
      aria-live="polite"
      aria-label="Loading page content"
    >
      <div className="flex flex-col items-center gap-4">
        <Loader2 className="w-12 h-12 animate-spin text-blue-600 dark:text-blue-400" />
        <p className="text-lg font-medium">Loading page...</p>
      </div>
    </div>
  );
}

export default PageLoader;
