import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'

/**
 * Enable MSW for development mode
 * This provides mock API endpoints without needing a running backend
 */
if (import.meta.env.DEV) {
  // Initialize mock token for development using TokenManager
  const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLXRlc3QiLCJ1c2VybmFtZSI6InRlc3QtdXNlciIsImVtYWlsIjoidGVzdEB0ZXN0LmNvbSIsIm5hbWUiOiJUZXN0IFVzZXIiLCJyb2xlIjoiU1RBRkYiLCJ0ZW5hbnRfaWQiOiJ0ZW5hbnQtMTIzIiwiZXhwIjoyNzE2MjM5MDIyfQ.7XQZ_7xWKb0O9xwK8j7G5zK5zK5zK5zK5zK5zK5z';
  
  // Only set mock token if no token exists
  const { tokenManager } = await import('./utils/TokenManager');
  if (!tokenManager.get()) {
    tokenManager.store(mockToken);
    console.debug('[main] Mock token initialized for development');
  }

  const { worker } = await import('./mocks/browser');
  await worker.start({
    onUnhandledRequest: 'bypass', // Allow unhandled requests to pass through
  });
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)

